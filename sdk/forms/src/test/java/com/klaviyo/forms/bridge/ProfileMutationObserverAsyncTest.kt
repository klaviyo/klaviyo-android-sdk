package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.ValidatedToken
import com.klaviyo.fixtures.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test

/**
 * Tests for [ProfileMutationObserver] behaviour when a [JwtObserver] is provided —
 * i.e. the coordination path used by [KlaviyoObserverCollection] to prevent profile injection
 * from racing ahead of JWT delivery.
 */
class ProfileMutationObserverAsyncTest : BaseTest() {

    private val stubProfile = Profile()
    private val stateMock = mockk<State>(relaxed = true).apply {
        every { getAsProfile() } returns stubProfile
    }
    private val mockBridge = mockk<JsBridge>(relaxed = true)

    @Before
    override fun setup() {
        super.setup()
        Registry.register<State>(stateMock)
        Registry.register<JsBridge>(mockBridge)
    }

    @After
    override fun cleanup() {
        Registry.unregister<State>()
        Registry.unregister<JsBridge>()
        super.cleanup()
    }

    @Test
    fun `startObserver awaits jwtReady before injecting profile`() {
        // JwtObserver created but not started — jwtReady is the initial incomplete deferred,
        // which we complete manually below to simulate JWT delivery.
        val jwtObserver = JwtObserver()
        ProfileMutationObserver(jwtObserver).startObserver()
        dispatcher.scheduler.runCurrent()

        // JWT not yet delivered — profile must not have been injected
        verify(inverse = true) { mockBridge.profileMutation(any()) }

        // JWT arrives — profile injection should now proceed
        jwtObserver.jwtReady.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockBridge.profileMutation(stubProfile) }
    }

    @Test
    fun `startObserver does not inject profile if jwtReady is cancelled`() {
        val jwtObserver = JwtObserver()
        val observer = ProfileMutationObserver(jwtObserver)
        observer.startObserver()
        dispatcher.scheduler.runCurrent()

        // WebView destroyed before JWT delivered — deferred is cancelled
        jwtObserver.jwtReady.cancel()
        dispatcher.scheduler.advanceUntilIdle()

        verify(inverse = true) { mockBridge.profileMutation(any()) }
    }

    @Test
    fun `startObserver works on reinit after stop — scope must not be permanently cancelled`() {
        // Regression test: the old implementation called scope.cancel() in stopObserver(),
        // which permanently destroyed the scope and made subsequent startObserver calls a no-op.
        val jwtObserver = JwtObserver()
        val observer = ProfileMutationObserver(jwtObserver)

        // First session: start and immediately stop
        observer.startObserver()
        observer.stopObserver()

        // Second session: should work even though stopObserver was previously called
        observer.startObserver()
        jwtObserver.jwtReady.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockBridge.profileMutation(stubProfile) }
    }

    @Test
    fun `startObserver reads jwtReady fresh each session — regression for capture-at-construction`() {
        val jwtObserver = JwtObserver()
        val observer = ProfileMutationObserver(jwtObserver)
        val sessionOneDeferred = jwtObserver.jwtReady

        observer.startObserver()
        sessionOneDeferred.cancel()
        dispatcher.scheduler.advanceUntilIdle()
        verify(inverse = true) { mockBridge.profileMutation(any()) }
        observer.stopObserver()

        val mockAuth = mockk<AuthTokenManager>()
        coEvery { mockAuth.currentToken(any()) } returns ValidatedToken(
            rawToken = "tok",
            expiresAtEpochSeconds = 0L,
            issuedAtEpochSeconds = 0L
        )
        every { mockAuth.onTokenRefresh(any()) } just runs
        every { mockAuth.offTokenRefresh(any()) } just runs
        Registry.register<AuthTokenManager>(mockAuth)
        try {
            jwtObserver.startObserver()
            dispatcher.scheduler.advanceUntilIdle()
            assertNotSame(sessionOneDeferred, jwtObserver.jwtReady)

            observer.startObserver()
            dispatcher.scheduler.advanceUntilIdle()

            verify(exactly = 1) { mockBridge.profileMutation(stubProfile) }

            jwtObserver.stopObserver()
        } finally {
            Registry.unregister<AuthTokenManager>()
        }
    }

    @Test
    fun `invoke with ProfileIdentifier triggers a JWT refresh before injecting the profile`() {
        val observerSlot = slot<StateChangeObserver>()
        every { stateMock.onStateChange(capture(observerSlot)) } returns Unit

        val mockAuth = mockk<AuthTokenManager>()
        coEvery { mockAuth.currentToken(any()) } returns ValidatedToken(
            rawToken = "refreshed",
            expiresAtEpochSeconds = 0L,
            issuedAtEpochSeconds = 0L
        )
        every { mockAuth.onTokenRefresh(any()) } just runs
        every { mockAuth.offTokenRefresh(any()) } just runs
        Registry.register<AuthTokenManager>(mockAuth)

        try {
            val jwtObserver = JwtObserver()
            jwtObserver.startObserver()
            dispatcher.scheduler.advanceUntilIdle()

            val observer = ProfileMutationObserver(jwtObserver)
            observer.startObserver()
            dispatcher.scheduler.advanceUntilIdle()

            observerSlot.captured.invoke(StateChange.ProfileIdentifier(mockk(relaxed = true), null))
            dispatcher.scheduler.advanceUntilIdle()

            // Once from JwtObserver's own initial fetch, once from the profile-identifier refresh —
            // forceReinject means the second call isn't deduped away despite the unchanged value.
            verify(exactly = 2) { mockBridge.jwtMutation("refreshed") }
        } finally {
            Registry.unregister<AuthTokenManager>()
        }
    }

    @Test
    fun `invoke with ProfileReset injects the profile but does not trigger a JWT refresh`() {
        val observerSlot = slot<StateChangeObserver>()
        every { stateMock.onStateChange(capture(observerSlot)) } returns Unit

        val mockAuth = mockk<AuthTokenManager>()
        coEvery { mockAuth.currentToken(any()) } returns ValidatedToken(
            rawToken = "initial",
            expiresAtEpochSeconds = 0L,
            issuedAtEpochSeconds = 0L
        )
        every { mockAuth.onTokenRefresh(any()) } just runs
        every { mockAuth.offTokenRefresh(any()) } just runs
        Registry.register<AuthTokenManager>(mockAuth)

        try {
            val jwtObserver = JwtObserver()
            jwtObserver.startObserver()
            dispatcher.scheduler.advanceUntilIdle()

            val observer = ProfileMutationObserver(jwtObserver)
            observer.startObserver()
            dispatcher.scheduler.advanceUntilIdle()

            observerSlot.captured.invoke(StateChange.ProfileReset(mockk(relaxed = true)))
            dispatcher.scheduler.advanceUntilIdle()

            // Only JwtObserver's own initial fetch — the bare reset must not trigger another one.
            coVerify(exactly = 1) { mockAuth.currentToken(any()) }
            verify(exactly = 1) { mockBridge.jwtMutation("initial") }
            // The profile is still re-injected for the reset — just without a JWT refresh alongside
            // it (initial injection at startObserver, plus one for this ProfileReset).
            verify(exactly = 2) { mockBridge.profileMutation(stubProfile) }
        } finally {
            Registry.unregister<AuthTokenManager>()
        }
    }
}

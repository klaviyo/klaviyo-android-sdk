package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.ValidatedToken
import com.klaviyo.fixtures.BaseTest
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Before
import org.junit.Test

class ProfileJwtDeliveryTest : BaseTest() {

    private val outgoing = Profile(email = "old@example.com")
    private val replacement = Profile(email = "new@example.com")
    private val stateObserver = slot<StateChangeObserver>()
    private val stateMock = mockk<State>(relaxed = true).apply {
        every { getAsProfile() } returns outgoing
        every { onStateChange(capture(stateObserver)) } just runs
    }
    private val mockBridge = mockk<JsBridge>(relaxed = true)
    private val auth = mockk<AuthTokenManager>().apply {
        every { onTokenRefresh(any()) } just runs
        every { offTokenRefresh(any()) } just runs
        every { isCurrentToken(any()) } returns true
    }
    private val jwtObserver by lazy { JwtObserver() }
    private val profileObserver by lazy { ProfileMutationObserver(jwtObserver) }

    @Before
    override fun setup() {
        super.setup()
        Registry.register<State>(stateMock)
        Registry.register<JsBridge>(mockBridge)
        Registry.register<AuthTokenManager>(auth)
    }

    @After
    override fun cleanup() {
        profileObserver.stopObserver()
        jwtObserver.stopObserver()
        Registry.unregister<State>()
        Registry.unregister<JsBridge>()
        Registry.unregister<AuthTokenManager>()
        super.cleanup()
    }

    private fun token(raw: String) = ValidatedToken(raw, 0L, 0L)

    /** Starts both observers in [JsBridgeObserverCollection] order, as on JsReady. */
    private fun startObservers() {
        jwtObserver.startObserver()
        dispatcher.scheduler.runCurrent()
        profileObserver.startObserver()
    }

    private fun replaceProfile() {
        every { stateMock.getAsProfile() } returns replacement
        stateObserver.captured.invoke(StateChange.ProfileReset(outgoing))
    }

    @Test
    fun `startObserver injects profile before a slow JWT resolves`() {
        val tokenCompletion = CompletableDeferred<ValidatedToken>()
        coEvery { auth.currentToken(any()) } coAnswers { tokenCompletion.await() }

        startObservers()

        verify(exactly = 1) { mockBridge.profileMutation(outgoing) }
        verify(exactly = 0) { mockBridge.jwtMutation(any()) }

        tokenCompletion.complete(token("late"))
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockBridge.jwtMutation("late") }
    }

    @Test
    fun `replacement clears outgoing JWT, publishes new profile, then delivers new JWT`() {
        coEvery { auth.currentToken(any()) } returns token("jwt-a")
        startObservers()
        dispatcher.scheduler.advanceUntilIdle()
        clearMocks(mockBridge, answers = false)

        coEvery { auth.currentToken(any()) } returns token("jwt-b")
        replaceProfile()
        dispatcher.scheduler.advanceUntilIdle()

        verifyOrder {
            mockBridge.jwtMutation("")
            mockBridge.profileMutation(replacement)
            mockBridge.jwtMutation("jwt-b")
        }
        verify(exactly = 0) { mockBridge.jwtMutation("jwt-a") }
    }

    @Test
    fun `profile change during the initial JWT fetch is published`() {
        val outgoingToken = CompletableDeferred<ValidatedToken>()
        coEvery { auth.currentToken(any()) } coAnswers { outgoingToken.await() }
        startObservers()

        coEvery { auth.currentToken(any()) } returns token("jwt-b")
        replaceProfile()
        outgoingToken.complete(token("jwt-a"))
        dispatcher.scheduler.advanceUntilIdle()

        verifyOrder {
            mockBridge.profileMutation(outgoing)
            mockBridge.jwtMutation("")
            mockBridge.profileMutation(replacement)
            mockBridge.jwtMutation("jwt-b")
        }
        verify(exactly = 0) { mockBridge.jwtMutation("jwt-a") }
    }
}

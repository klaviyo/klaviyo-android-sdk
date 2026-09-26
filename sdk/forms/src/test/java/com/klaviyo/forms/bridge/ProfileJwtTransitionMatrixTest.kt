package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.TokenRefreshObserver
import com.klaviyo.core.auth.ValidatedToken
import com.klaviyo.fixtures.BaseTest
import io.mockk.CapturingSlot
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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class ProfileJwtTransitionMatrixTest : BaseTest() {
    private val stateObservers = mutableListOf<StateChangeObserver>()
    private val state = mockk<State>(relaxed = true).apply {
        every { onStateChange(any()) } answers { stateObservers.add(firstArg<StateChangeObserver>()) }
        every { offStateChange(any()) } just runs
    }
    private val bridge = mockk<JsBridge>(relaxed = true)
    private val auth = mockk<AuthTokenManager>().apply {
        every { onTokenRefresh(any()) } just runs
        every { offTokenRefresh(any()) } just runs
        every { onTokenInvalidated(any()) } just runs
        every { offTokenInvalidated(any()) } just runs
    }

    @Before
    override fun setup() {
        super.setup()
        Registry.register<State>(state)
        Registry.register<JsBridge>(bridge)
        Registry.register<AuthTokenManager>(auth)
    }

    @After
    override fun cleanup() {
        Registry.unregister<State>()
        Registry.unregister<JsBridge>()
        Registry.unregister<AuthTokenManager>()
        super.cleanup()
    }

    @Test
    fun `live webview matrix retains compatible JWT and clears every replacement`() = runTest(
        dispatcher
    ) {
        val retained = Profile(email = EMAIL, externalId = EXTERNAL_ID)
        val reduced = Profile(email = EMAIL)
        every { state.getAsProfile() } returns retained andThen retained andThen reduced
        coEvery { auth.currentToken(any()) } returns ValidatedToken("retained", 0L, 0L)
        val collection = KlaviyoObserverCollection()

        collection.startObservers(NativeBridgeMessage.JsReady)
        dispatcher.scheduler.advanceUntilIdle()
        stateObservers.forEach { it(StateChange.ProfileIdentifier(mockk(), null)) }
        stateObservers.forEach { it(StateChange.ProfileIdentifier(mockk(), EXTERNAL_ID)) }
        stateObservers.forEach { it(StateChange.ProfileReset(retained)) }

        verify(exactly = 1) { bridge.jwtMutation("retained") }
        verify(exactly = 1) { bridge.jwtMutation("") }
        verifyOrder {
            bridge.profileMutation(reduced)
            bridge.jwtMutation("")
        }
        collection.stopObservers()
    }

    @Test
    fun `fresh webview matrix delivers only fresh JWT after replacement`() = runTest(dispatcher) {
        val refresh = refreshObserver()
        val outgoing = Profile(email = "old@example.com")
        val replacement = Profile(email = "new@example.com")
        every { state.getAsProfile() } returns outgoing andThen replacement
        coEvery { auth.currentToken(any()) } returns ValidatedToken("old", 0L, 0L)
        val first = KlaviyoObserverCollection()
        first.startObservers(NativeBridgeMessage.JsReady)
        dispatcher.scheduler.advanceUntilIdle()
        first.stopObservers()
        clearMocks(bridge, answers = false)
        val outgoingFetch = CompletableDeferred<ValidatedToken>()
        coEvery { auth.currentToken(any()) } coAnswers { outgoingFetch.await() }
        val fresh = KlaviyoObserverCollection()

        fresh.startObservers(NativeBridgeMessage.JsReady)
        dispatcher.scheduler.advanceUntilIdle()
        stateObservers.forEach { it(StateChange.ProfileReset(outgoing)) }
        refresh.captured("fresh") { true }
        outgoingFetch.complete(ValidatedToken("old", 0L, 0L))
        dispatcher.scheduler.advanceUntilIdle()

        verify(inverse = true) { bridge.jwtMutation("old") }
        verifyOrder {
            bridge.profileMutation(replacement)
            bridge.jwtMutation("")
            bridge.jwtMutation("fresh")
        }
        fresh.stopObservers()
    }

    private fun refreshObserver(): CapturingSlot<TokenRefreshObserver> = slot<TokenRefreshObserver>().also {
        every { auth.onTokenRefresh(capture(it)) } just runs
    }
}

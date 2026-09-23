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

class ProfileMutationObserverAsyncTest : BaseTest() {

    private val stubProfile = Profile(email = EMAIL)
    private val stateObserver = slot<StateChangeObserver>()
    private val stateMock = mockk<State>(relaxed = true).apply {
        every { getAsProfile() } returns stubProfile
        every { onStateChange(capture(stateObserver)) } just runs
    }
    private val mockBridge = mockk<JsBridge>(relaxed = true)
    private val mockAuth = mockk<AuthTokenManager>().apply {
        every { onTokenRefresh(any()) } just runs
        every { offTokenRefresh(any()) } just runs
        every { onTokenInvalidated(any()) } just runs
        every { offTokenInvalidated(any()) } just runs
    }

    private fun captureRefreshObserver(): CapturingSlot<TokenRefreshObserver> =
        slot<TokenRefreshObserver>().also { observer ->
            every { mockAuth.onTokenRefresh(capture(observer)) } just runs
        }

    @Before
    override fun setup() {
        super.setup()
        Registry.register<State>(stateMock)
        Registry.register<JsBridge>(mockBridge)
        Registry.register<AuthTokenManager>(mockAuth)
    }

    @After
    override fun cleanup() {
        Registry.unregister<State>()
        Registry.unregister<JsBridge>()
        Registry.unregister<AuthTokenManager>()
        super.cleanup()
    }

    @Test
    fun `profile delivery does not wait for a slow initial JWT`() {
        val token = CompletableDeferred<ValidatedToken>()
        coEvery { mockAuth.currentToken(any()) } coAnswers { token.await() }
        val jwtObserver = JwtObserver()

        jwtObserver.startObserver()
        dispatcher.scheduler.runCurrent()
        ProfileMutationObserver(jwtObserver).startObserver()

        verify(exactly = 1) { mockBridge.profileMutation(stubProfile) }
        verify(exactly = 0) { mockBridge.jwtMutation(any()) }

        token.complete(ValidatedToken("late", 0L, 0L))
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockBridge.jwtMutation("late") }
        jwtObserver.stopObserver()
    }

    @Test
    fun `initial JWT may arrive before profile delivery`() {
        coEvery { mockAuth.currentToken(any()) } returns ValidatedToken("early", 0L, 0L)
        val jwtObserver = JwtObserver()

        jwtObserver.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        ProfileMutationObserver(jwtObserver).startObserver()

        verifyOrder {
            mockBridge.jwtMutation("early")
            mockBridge.profileMutation(stubProfile)
        }
        jwtObserver.stopObserver()
    }

    @Test
    fun `profile reset clear prevents queued initial JWT reinjection`() {
        val uiQueue = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers { uiQueue += firstArg<() -> Unit>() }
        coEvery { mockAuth.currentToken(any()) } returns ValidatedToken("stale", 0L, 0L)
        val jwtObserver = JwtObserver()
        val profileObserver = ProfileMutationObserver(jwtObserver)

        jwtObserver.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        profileObserver.startObserver()
        stateObserver.captured.invoke(StateChange.ProfileReset(Profile()))

        uiQueue[1].invoke()
        uiQueue[0].invoke()

        verify(exactly = 1) { mockBridge.jwtMutation("") }
        verify(exactly = 0) { mockBridge.jwtMutation("stale") }
    }

    @Test
    fun `profile reset clear allows unchanged token to be reinjected`() {
        val refreshObserver = captureRefreshObserver()
        coEvery { mockAuth.currentToken(any()) } returns ValidatedToken("same-token", 0L, 0L)
        val jwtObserver = JwtObserver()
        val profileObserver = ProfileMutationObserver(jwtObserver)

        jwtObserver.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        profileObserver.startObserver()
        stateObserver.captured.invoke(StateChange.ProfileReset(Profile()))
        refreshObserver.captured.invoke("same-token") { true }

        verify(exactly = 1) { mockBridge.jwtMutation("") }
        verify(exactly = 2) { mockBridge.jwtMutation("same-token") }
    }

    @Test
    fun `profile replacement rejects outgoing refresh paused before observer dispatch`() {
        val refreshObserver = captureRefreshObserver()
        val initialToken = CompletableDeferred<ValidatedToken>()
        val uiQueue = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers { uiQueue += firstArg<() -> Unit>() }
        coEvery { mockAuth.currentToken(any()) } coAnswers { initialToken.await() }
        val replacement = Profile(email = "replacement@klaviyo.com")
        every { stateMock.getAsProfile() } returns stubProfile andThen replacement
        val jwtObserver = JwtObserver()
        val profileObserver = ProfileMutationObserver(jwtObserver)

        jwtObserver.startObserver()
        dispatcher.scheduler.runCurrent()
        profileObserver.startObserver()

        var outgoingGenerationCurrent = true
        val isOutgoingTokenCurrent = { outgoingGenerationCurrent }
        outgoingGenerationCurrent = false
        stateObserver.captured.invoke(StateChange.ProfileReset(stubProfile))
        refreshObserver.captured.invoke("outgoing-token", isOutgoingTokenCurrent)
        uiQueue.forEach { it.invoke() }

        verifyOrder {
            mockBridge.profileMutation(stubProfile)
            mockBridge.profileMutation(replacement)
            mockBridge.jwtMutation("")
        }
        verify(exactly = 0) { mockBridge.jwtMutation("outgoing-token") }
    }
}

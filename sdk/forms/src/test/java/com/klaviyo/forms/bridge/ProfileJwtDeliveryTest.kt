package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenException
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.TokenRefreshObserver
import com.klaviyo.core.auth.ValidatedToken
import com.klaviyo.fixtures.BaseTest
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ProfileJwtDeliveryTest : BaseTest() {

    private val outgoing = Profile(email = "old@example.com")
    private val replacement = Profile(email = "new@example.com")
    private val compatible = Profile(externalId = "external", email = "old@example.com")
    private val externalIdKey = mockk<ProfileKey>(relaxed = true)
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
        every { profileGeneration() } answers { profileGeneration }
    }
    private var profileGeneration = 0L
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

    /** Simulates analytics state side effects invalidating the outgoing profile's JWT. */
    private fun invalidateOutgoing() {
        profileGeneration++
        every { auth.isCurrentToken("jwt-a") } returns false
    }

    /** Simulates a replacement, after state side effects have invalidated the outgoing JWT. */
    private fun replaceProfile() {
        invalidateOutgoing()
        every { stateMock.getAsProfile() } returns replacement
        stateObserver.captured.invoke(StateChange.ProfileReset(outgoing))
    }

    /**
     * Queue UI work like the main looper and record webview writes in the order they run,
     * as [com.klaviyo.forms.webview.KlaviyoWebViewClient.evaluateJavascript] posts them.
     */
    private fun recordWebViewWrites(): Pair<MutableList<() -> Unit>, MutableList<String>> {
        val uiQueue = mutableListOf<() -> Unit>()
        val writes = mutableListOf<String>()
        every { mockThreadHelper.runOnUiThread(any()) } answers { uiQueue.add(firstArg()) }
        every { mockBridge.profileMutation(any()) } answers {
            val profile = firstArg<Profile>()
            uiQueue.add { writes += "profile:${profile.email}" }
        }
        every { mockBridge.jwtMutation(any()) } answers {
            val jwt = firstArg<String>()
            uiQueue.add { writes += "jwt:$jwt" }
        }
        return uiQueue to writes
    }

    private fun MutableList<() -> Unit>.drain() {
        while (isNotEmpty()) {
            removeAt(0).invoke()
            dispatcher.scheduler.advanceUntilIdle()
        }
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
    fun `replacement publishes new profile, then delivers new JWT`() {
        coEvery { auth.currentToken(any()) } returns token("jwt-a")
        startObservers()
        dispatcher.scheduler.advanceUntilIdle()
        clearMocks(mockBridge, answers = false)

        coEvery { auth.currentToken(any()) } returns token("jwt-b")
        replaceProfile()
        dispatcher.scheduler.advanceUntilIdle()

        verifyOrder {
            mockBridge.profileMutation(replacement)
            mockBridge.jwtMutation("jwt-b")
        }
        verify(exactly = 0) { mockBridge.jwtMutation("jwt-a") }
        verify(exactly = 0) { mockBridge.jwtMutation("") }
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
            mockBridge.profileMutation(replacement)
            mockBridge.jwtMutation("jwt-b")
        }
        verify(exactly = 0) { mockBridge.jwtMutation("jwt-a") }
    }

    @Test
    fun `compatible change publishes profile without refetching or injecting a JWT`() {
        coEvery { auth.currentToken(any()) } returns token("jwt-a")
        startObservers()
        dispatcher.scheduler.advanceUntilIdle()
        clearMocks(mockBridge, answers = false)
        clearMocks(auth, answers = false)

        every { stateMock.getAsProfile() } returns compatible
        stateObserver.captured.invoke(StateChange.ProfileIdentifier(externalIdKey, null))
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockBridge.profileMutation(compatible) }
        verify(exactly = 0) { mockBridge.jwtMutation(any()) }
        coVerify(exactly = 0) { auth.currentToken(any()) }
    }

    @Test
    fun `replacement after a compatible change still delivers the new JWT`() {
        coEvery { auth.currentToken(any()) } returns token("jwt-a")
        startObservers()
        dispatcher.scheduler.advanceUntilIdle()
        every { stateMock.getAsProfile() } returns compatible
        stateObserver.captured.invoke(StateChange.ProfileIdentifier(externalIdKey, null))
        dispatcher.scheduler.advanceUntilIdle()
        clearMocks(mockBridge, answers = false)

        coEvery { auth.currentToken(any()) } returns token("jwt-b")
        replaceProfile()
        dispatcher.scheduler.advanceUntilIdle()

        verifyOrder {
            mockBridge.profileMutation(replacement)
            mockBridge.jwtMutation("jwt-b")
        }
        verify(exactly = 0) { mockBridge.jwtMutation("") }
    }

    @Test
    fun `profile reaches the webview before a prewarmed JWT delivered ahead of it`() {
        val refreshObserver = slot<TokenRefreshObserver>()
        every { auth.onTokenRefresh(capture(refreshObserver)) } just runs
        coEvery { auth.currentToken(any()) } returns token("jwt-a")
        val (uiQueue, writes) = recordWebViewWrites()
        startObservers()
        dispatcher.scheduler.advanceUntilIdle()
        uiQueue.drain()
        writes.clear()

        invalidateOutgoing()
        coEvery { auth.currentToken(any()) } returns token("jwt-b")
        refreshObserver.captured.invoke("jwt-b")
        every { stateMock.getAsProfile() } returns replacement
        stateObserver.captured.invoke(StateChange.ProfileReset(outgoing))
        uiQueue.drain()

        assertEquals(listOf("profile:new@example.com", "jwt:jwt-b"), writes)
    }

    @Test
    fun `anonymous to identified publishes the profile, then the first JWT`() {
        coEvery { auth.currentToken(any()) } throws AuthTokenException.NotIdentified
        every { stateMock.getAsProfile() } returns Profile()
        val (uiQueue, writes) = recordWebViewWrites()
        startObservers()
        dispatcher.scheduler.advanceUntilIdle()
        uiQueue.drain()
        writes.clear()

        profileGeneration++
        coEvery { auth.currentToken(any()) } returns token("jwt-b")
        every { stateMock.getAsProfile() } returns replacement
        stateObserver.captured.invoke(StateChange.ProfileIdentifier(externalIdKey, null))
        uiQueue.drain()

        assertEquals(listOf("profile:new@example.com", "jwt:jwt-b"), writes)
        verify(exactly = 0) { spyLog.warning(any(), any()) }
    }

    @Test
    fun `identified to anonymous publishes the profile and writes no JWT`() {
        coEvery { auth.currentToken(any()) } returns token("jwt-a")
        startObservers()
        dispatcher.scheduler.advanceUntilIdle()
        clearMocks(mockBridge, answers = false)

        invalidateOutgoing()
        coEvery { auth.currentToken(any()) } throws AuthTokenException.NotIdentified
        every { stateMock.getAsProfile() } returns Profile()
        stateObserver.captured.invoke(StateChange.ProfileReset(outgoing))
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockBridge.profileMutation(any()) }
        verify(exactly = 0) { mockBridge.jwtMutation(any()) }
        verify(exactly = 0) { spyLog.warning(any(), any()) }
    }
}

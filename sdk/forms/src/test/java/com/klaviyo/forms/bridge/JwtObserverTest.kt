package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenException
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.TokenRefreshObserver
import com.klaviyo.core.auth.ValidatedToken
import com.klaviyo.fixtures.BaseTest
import io.mockk.CapturingSlot
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JwtObserverTest : BaseTest() {

    private val mockAuthTokenManager = mockk<AuthTokenManager>()
    private val mockJsBridge = mockk<JsBridge>(relaxed = true)
    private var profileGeneration = 0L

    /** Start the observer and publish the profile, as [ProfileMutationObserver] does on start. */
    private fun JwtObserver.start() = apply {
        startObserver()
        publishProfile {}
    }

    private fun validatedToken(rawToken: String): ValidatedToken =
        ValidatedToken(rawToken = rawToken, expiresAtEpochSeconds = 0L, issuedAtEpochSeconds = 0L)

    /** Registers a capturing [TokenRefreshObserver] so a test can drive proactive refreshes. */
    private fun captureRefreshObserver(): CapturingSlot<TokenRefreshObserver> =
        slot<TokenRefreshObserver>().also { slot ->
            every { mockAuthTokenManager.onTokenRefresh(capture(slot)) } just runs
        }

    @Before
    override fun setup() {
        super.setup()
        Registry.register<AuthTokenManager>(mockAuthTokenManager)
        Registry.register<JsBridge>(mockJsBridge)
        every { mockAuthTokenManager.onTokenRefresh(any()) } just runs
        every { mockAuthTokenManager.offTokenRefresh(any()) } just runs
        every { mockAuthTokenManager.isCurrentToken(any()) } returns true
        every { mockAuthTokenManager.profileGeneration() } answers { profileGeneration }
    }

    @After
    override fun cleanup() {
        Registry.unregister<AuthTokenManager>()
        Registry.unregister<JsBridge>()
        super.cleanup()
    }

    @Test
    fun `startOn defaults to JsReady`() {
        assert(JwtObserver().startOn == NativeBridgeMessage.JsReady)
    }

    @Test
    fun `startObserver injects token when fetch succeeds`() {
        val token = "header.payload.signature"
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken(token)

        JwtObserver().start()
        dispatcher.scheduler.advanceUntilIdle()

        verify { mockJsBridge.jwtMutation(token) }
    }

    @Test
    fun `startObserver injects nothing and logs debug when no provider registered`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } throws
            AuthTokenException.NoProviderRegistered

        JwtObserver().start()
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { mockJsBridge.jwtMutation(any()) }
        verify { spyLog.debug(match { it.contains("Auth not enabled") }) }
    }

    @Test
    fun `startObserver injects nothing and logs warning when fetch fails`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } throws
            AuthTokenException.ValidationFailed("Malformed")

        JwtObserver().start()
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { mockJsBridge.jwtMutation(any()) }
        verify { spyLog.warning(match { it.contains("Auth token fetch failed") }) }
    }

    @Test
    fun `stopObserver cancels in-flight fetch before injection`() {
        val tokenCompletion = CompletableDeferred<ValidatedToken>()
        coEvery { mockAuthTokenManager.currentToken(any()) } coAnswers { tokenCompletion.await() }

        val observer = JwtObserver()
        observer.start()
        dispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { mockAuthTokenManager.currentToken(any()) }

        observer.stopObserver()
        tokenCompletion.complete(validatedToken("would-be-token"))
        dispatcher.scheduler.advanceUntilIdle()

        verify(inverse = true) { mockJsBridge.jwtMutation(any()) }
    }

    @Test
    fun `double startObserver cancels previous in-flight fetch`() {
        val firstCompletion = CompletableDeferred<ValidatedToken>()
        val secondToken = "second.token.value"
        coEvery { mockAuthTokenManager.currentToken(any()) } coAnswers { firstCompletion.await() }

        val observer = JwtObserver()
        observer.start()
        dispatcher.scheduler.runCurrent()

        // Second start before first fetch completes — should cancel the first job
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken(secondToken)
        observer.start()
        firstCompletion.complete(validatedToken("first.token.value"))
        dispatcher.scheduler.advanceUntilIdle()

        // Only the second token should be injected
        verify(exactly = 1) { mockJsBridge.jwtMutation(secondToken) }
        verify(inverse = true) { mockJsBridge.jwtMutation("first.token.value") }
    }

    @Test
    fun `queued ui callback from a superseded fetch does not inject its stale token`() {
        // In prod, runOnUiThread posts to the real UI thread. If currentToken returns synchronously
        // (cached) the first fetch can queue its UI callback before the second startObserver runs.
        // Override the relaxed runOnUiThread answer with a manual queue so the test can observe the
        // queued ordering.
        val uiQueue = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers {
            uiQueue.add(firstArg())
        }

        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("stale")
        val observer = JwtObserver()
        observer.start()
        dispatcher.scheduler.advanceUntilIdle()

        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("fresh")
        observer.start()
        dispatcher.scheduler.advanceUntilIdle()

        uiQueue.forEach { it.invoke() }

        verify(inverse = true) { mockJsBridge.jwtMutation("stale") }
        verify(exactly = 1) { mockJsBridge.jwtMutation("fresh") }
    }

    @Test
    fun `startObserver re-injects the token when it is proactively refreshed`() {
        val refreshObserver = captureRefreshObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("initial")

        val observer = JwtObserver()
        observer.start()
        dispatcher.scheduler.advanceUntilIdle()

        refreshObserver.captured.invoke("refreshed.token")

        verify(exactly = 1) { mockJsBridge.jwtMutation("initial") }
        verify(exactly = 1) { mockJsBridge.jwtMutation("refreshed.token") }
    }

    @Test
    fun `identical token from initial fetch and refresh stream is injected only once`() {
        // A fast initial fetch resolves within the interactive budget, and the manager also echoes
        // the same value on its onTokenRefresh broadcast (which now fires on every acquisition).
        // The webview should see the unchanged token only once.
        val refreshObserver = captureRefreshObserver()
        val token = "same.token.value"
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken(token)

        val observer = JwtObserver()
        observer.start()
        dispatcher.scheduler.advanceUntilIdle()

        refreshObserver.captured.invoke(token)

        verify(exactly = 1) { mockJsBridge.jwtMutation(token) }
    }

    @Test
    fun `a refresh queued in a previous session does not inject into the new webview`() {
        // A proactive refresh dispatched during session 1 queues its UI callback, then the form is
        // closed and reopened (session 2) before that callback runs. The stale refresh must not
        // reach the fresh webview.
        val uiQueue = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers { uiQueue.add(firstArg()) }

        val refreshObserver = captureRefreshObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("session-1")

        val observer = JwtObserver()
        observer.start() // session 1
        dispatcher.scheduler.advanceUntilIdle() // session-1 fetch queues its inject
        refreshObserver.captured.invoke("stale-refresh") // session-1 refresh queues its inject

        observer.stopObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("session-2")
        observer.start() // session 2 (fresh webview)
        dispatcher.scheduler.advanceUntilIdle() // session-2 fetch queues its inject

        uiQueue.forEach { it.invoke() }

        verify(inverse = true) { mockJsBridge.jwtMutation("stale-refresh") }
        verify(exactly = 1) { mockJsBridge.jwtMutation("session-2") }
    }

    @Test
    fun `unchanged token is re-injected into a fresh webview after a form restart`() {
        // JwtObserver is a shared instance across form sessions, but each session loads a new
        // webview with no JWT. The value-dedup must be scoped to the current webview: reopening a
        // form while the same token is still valid must re-deliver it rather than skip it.
        val token = "header.payload.signature"
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken(token)

        val observer = JwtObserver()
        observer.start()
        dispatcher.scheduler.advanceUntilIdle()
        observer.stopObserver()

        observer.start()
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 2) { mockJsBridge.jwtMutation(token) }
    }

    @Test
    fun `refresh after stopObserver does not inject the stale token`() {
        val refreshObserver = captureRefreshObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("initial")

        val observer = JwtObserver()
        observer.start()
        dispatcher.scheduler.advanceUntilIdle()
        val captured = refreshObserver.captured

        observer.stopObserver()
        captured.invoke("late.token")

        verify { mockAuthTokenManager.offTokenRefresh(any()) }
        verify(inverse = true) { mockJsBridge.jwtMutation("late.token") }
    }

    @Test
    fun `a stale initial fetch does not clobber a token already delivered by refresh`() {
        // Queue UI callbacks manually so the test can force the initial fetch's callback to run
        // AFTER the refresh callback — the ordering that would let a stale cached token overwrite
        // the fresher refreshed one without the sequence guard.
        val uiQueue = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers {
            uiQueue.add(firstArg())
        }

        val refreshObserver = captureRefreshObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("stale-cached")

        val observer = JwtObserver()
        observer.start()
        uiQueue.removeAt(0).invoke() // profile publish
        dispatcher.scheduler.advanceUntilIdle() // initial fetch queues its UI callback (index 0)

        refreshObserver.captured.invoke("fresh-refreshed") // refresh queues its callback (index 1)

        // Run the refresh callback first, then the stale initial callback.
        uiQueue[1].invoke()
        uiQueue[0].invoke()

        verify(exactly = 1) { mockJsBridge.jwtMutation("fresh-refreshed") }
        verify(inverse = true) { mockJsBridge.jwtMutation("stale-cached") }
    }

    @Test
    fun `a failed initial fetch does not clobber a token delivered by an in-flight refresh`() {
        // The initial fetch is still suspended when a proactive refresh delivers a fresh token.
        // Its later failure must not overwrite the fresher refreshed token.
        val refreshObserver = captureRefreshObserver()
        val fetchCompletion = CompletableDeferred<ValidatedToken>()
        coEvery { mockAuthTokenManager.currentToken(any()) } coAnswers { fetchCompletion.await() }

        val observer = JwtObserver()
        observer.start()
        dispatcher.scheduler.runCurrent() // initial fetch launched and suspended on currentToken

        // Refresh lands while the initial fetch is still in flight.
        refreshObserver.captured.invoke("fresh-refreshed")

        // Initial fetch then fails.
        fetchCompletion.completeExceptionally(AuthTokenException.ValidationFailed("Malformed"))
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockJsBridge.jwtMutation("fresh-refreshed") }
        verify(exactly = 0) { mockJsBridge.jwtMutation("") }
    }

    @Test
    fun `startObserver deregisters before registering so the refresh observer is not duplicated`() {
        val offObserver = slot<TokenRefreshObserver>()
        val onObserver = slot<TokenRefreshObserver>()
        every { mockAuthTokenManager.offTokenRefresh(capture(offObserver)) } just runs
        every { mockAuthTokenManager.onTokenRefresh(capture(onObserver)) } just runs
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("initial")

        val observer = JwtObserver()
        observer.start()
        dispatcher.scheduler.advanceUntilIdle()

        verifyOrder {
            mockAuthTokenManager.offTokenRefresh(any())
            mockAuthTokenManager.onTokenRefresh(any())
        }
        assertSame(offObserver.captured, onObserver.captured)
    }

    @Test
    fun `refetchToken does nothing while stopped`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("token")
        val observer = JwtObserver()
        observer.start()
        dispatcher.scheduler.advanceUntilIdle()
        observer.stopObserver()

        observer.refetchToken()
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockJsBridge.jwtMutation(any()) }
        coVerify(exactly = 1) { mockAuthTokenManager.currentToken(any()) }
    }

    @Test
    fun `JWT-A from the initial fetch or a late echo is discarded after profile invalidation`() {
        val refreshObserver = captureRefreshObserver()
        val uiQueue = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers { uiQueue.add(firstArg()) }
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("jwt-a")
        val observer = JwtObserver()
        observer.start()
        dispatcher.scheduler.advanceUntilIdle()

        every { mockAuthTokenManager.isCurrentToken("jwt-a") } returns false
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("jwt-b")
        observer.refetchToken()
        refreshObserver.captured.invoke("jwt-a")
        dispatcher.scheduler.advanceUntilIdle()
        uiQueue.forEach { it.invoke() }

        verify(exactly = 0) { mockJsBridge.jwtMutation("jwt-a") }
        verify(exactly = 0) { mockJsBridge.jwtMutation("") }
        verify(exactly = 1) { mockJsBridge.jwtMutation("jwt-b") }
        coVerify(exactly = 1) {
            mockAuthTokenManager.currentToken(AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS)
        }
    }

    @Test
    fun `unchanged token is re-injected once after a profile change`() {
        val refreshObserver = captureRefreshObserver()
        val token = "same.token.value"
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken(token)
        val observer = JwtObserver()
        observer.start()
        dispatcher.scheduler.advanceUntilIdle()

        observer.refetchToken()
        dispatcher.scheduler.advanceUntilIdle()
        refreshObserver.captured.invoke(token)

        verify(exactly = 2) { mockJsBridge.jwtMutation(token) }
    }

    @Test
    fun `token completing after interactive timeout reaches the same WebView`() {
        val refreshObserver = captureRefreshObserver()
        val lateToken = validatedToken("late.token.value")
        coEvery { mockAuthTokenManager.currentToken(any()) } throws AuthTokenException.TimedOut
        val observer = JwtObserver()

        observer.start()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            mockAuthTokenManager.currentToken(AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS)
        }

        verify(exactly = 0) { mockJsBridge.jwtMutation("") }

        refreshObserver.captured.invoke(lateToken.rawToken)

        verify(exactly = 1) { mockJsBridge.jwtMutation(lateToken.rawToken) }
        coVerify(exactly = 1) { mockAuthTokenManager.currentToken(any()) }
    }

    @Test
    fun `publishProfile without a replacement does not refetch`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("jwt-a")
        val observer = JwtObserver().start()
        dispatcher.scheduler.advanceUntilIdle()

        var published = false
        observer.publishProfile { published = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(published)
        verify(exactly = 1) { mockJsBridge.jwtMutation("jwt-a") }
        coVerify(exactly = 1) { mockAuthTokenManager.currentToken(any()) }
    }

    @Test
    fun `publishProfile after a replacement refetches and injects the new token`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("jwt-a")
        val observer = JwtObserver().start()
        dispatcher.scheduler.advanceUntilIdle()

        replaceProfile()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("jwt-b")
        observer.publishProfile {}
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockJsBridge.jwtMutation("jwt-b") }
        verify(exactly = 0) { mockJsBridge.jwtMutation("") }
        coVerify(exactly = 1) {
            mockAuthTokenManager.currentToken(AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS)
        }
    }

    @Test
    fun `token for a replaced profile is withheld until that profile is published`() {
        val refreshObserver = captureRefreshObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("jwt-a")
        val observer = JwtObserver().start()
        dispatcher.scheduler.advanceUntilIdle()
        val writes = mutableListOf<String>()
        every { mockJsBridge.jwtMutation(any()) } answers { writes += "jwt:${firstArg<String>()}" }

        replaceProfile()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("jwt-b")
        refreshObserver.captured.invoke("jwt-b")
        assertTrue(writes.isEmpty())

        observer.publishProfile { writes += "profile" }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("profile", "jwt:jwt-b"), writes)
    }

    @Test
    fun `token fetched before the first profile publish is injected after it`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("jwt-a")
        val observer = JwtObserver()
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 0) { mockJsBridge.jwtMutation(any()) }

        observer.publishProfile {}
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockJsBridge.jwtMutation("jwt-a") }
    }

    @Test
    fun `publishProfile while stopped publishes without fetching`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("jwt-a")
        val observer = JwtObserver()
        var published = false

        observer.publishProfile { published = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(published)
        coVerify(exactly = 0) { mockAuthTokenManager.currentToken(any()) }
    }

    @Test
    fun `unidentified profile resolves without a JWT or a failure log`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } throws AuthTokenException.NotIdentified

        JwtObserver().start()
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { mockJsBridge.jwtMutation(any()) }
        verify(exactly = 0) { spyLog.warning(any(), any()) }
        verify(exactly = 0) { spyLog.error(any(), any()) }
    }

    @Test
    fun `a token withheld in a previous webview does not cause a refetch in a rebuilt one`() {
        val refreshObserver = captureRefreshObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("jwt-a")
        val observer = JwtObserver().start()
        dispatcher.scheduler.advanceUntilIdle()
        replaceProfile()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("jwt-b")
        refreshObserver.captured.invoke("jwt-b")
        observer.stopObserver()
        clearMocks(mockAuthTokenManager, answers = false)

        observer.start()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockAuthTokenManager.currentToken(any()) }
        verify(exactly = 1) { mockJsBridge.jwtMutation("jwt-b") }
    }

    @Test
    fun `a profile published to a previous webview does not release a token to a rebuilt one`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("jwt-a")
        val observer = JwtObserver().start()
        dispatcher.scheduler.advanceUntilIdle()
        observer.stopObserver()
        clearMocks(mockJsBridge, answers = false)

        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 0) { mockJsBridge.jwtMutation(any()) }

        observer.publishProfile {}
        dispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 1) { mockJsBridge.jwtMutation("jwt-a") }
    }

    /** Simulates analytics invalidating the outgoing profile's token. */
    private fun replaceProfile() {
        profileGeneration++
        every { mockAuthTokenManager.isCurrentToken("jwt-a") } returns false
    }
}

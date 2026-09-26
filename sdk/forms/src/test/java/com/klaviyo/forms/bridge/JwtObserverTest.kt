package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenException
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.TokenInvalidationObserver
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JwtObserverTest : BaseTest() {

    private val mockAuthTokenManager = mockk<AuthTokenManager>()
    private val mockJsBridge = mockk<JsBridge>(relaxed = true)

    private fun validatedToken(rawToken: String): ValidatedToken =
        ValidatedToken(rawToken = rawToken, expiresAtEpochSeconds = 0L, issuedAtEpochSeconds = 0L)

    /** Registers a capturing [TokenRefreshObserver] so a test can drive proactive refreshes. */
    private fun captureRefreshObserver(): CapturingSlot<TokenRefreshObserver> =
        slot<TokenRefreshObserver>().also { slot ->
            every { mockAuthTokenManager.onTokenRefresh(capture(slot)) } just runs
        }

    private fun captureInvalidationObserver(): CapturingSlot<TokenInvalidationObserver> =
        slot<TokenInvalidationObserver>().also { slot ->
            every { mockAuthTokenManager.onTokenInvalidated(capture(slot)) } just runs
        }

    @Before
    override fun setup() {
        super.setup()
        Registry.register<AuthTokenManager>(mockAuthTokenManager)
        Registry.register<JsBridge>(mockJsBridge)
        every { mockAuthTokenManager.onTokenRefresh(any()) } just runs
        every { mockAuthTokenManager.offTokenRefresh(any()) } just runs
        every { mockAuthTokenManager.isCurrentToken(any()) } returns true
        every { mockAuthTokenManager.onTokenInvalidated(any()) } just runs
        every { mockAuthTokenManager.offTokenInvalidated(any()) } just runs
    }

    @After
    override fun cleanup() {
        Registry.unregister<AuthTokenManager>()
        Registry.unregister<JsBridge>()
        super.cleanup()
    }

    @Test
    fun `startOn defaults to JsReady for independent JWT delivery`() {
        assert(JwtObserver().startOn == NativeBridgeMessage.JsReady)
    }

    @Test
    fun `token invalidation clears JWT in the active webview`() {
        val invalidationObserver = captureInvalidationObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("initial")
        val observer = JwtObserver()
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        invalidationObserver.captured.invoke()

        verify(exactly = 1) { mockJsBridge.jwtMutation("") }
    }

    @Test
    fun `token invalidation queued after stop cannot clear a new session`() {
        val invalidationObserver = captureInvalidationObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("initial")
        val observer = JwtObserver()
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        val oldCallback = invalidationObserver.captured
        observer.stopObserver()
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        clearMocks(mockJsBridge, answers = false)

        oldCallback.invoke()

        verify(exactly = 0) { mockJsBridge.jwtMutation("") }
    }

    @Test
    fun `stale clear cannot outrank a restarted session token`() {
        val invalidationObserver = captureInvalidationObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("initial")
        val observer = JwtObserver()
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        val oldCallback = invalidationObserver.captured
        clearMocks(mockJsBridge, answers = false)

        val clearAtSequence = CountDownLatch(1)
        val releaseClear = CountDownLatch(1)
        val sequence = AtomicLong(1)
        val controlledSequence = mockk<AtomicLong>()
        every { controlledSequence.incrementAndGet() } answers {
            if (Thread.currentThread().name == "stale-clear") {
                clearAtSequence.countDown()
                assertTrue(releaseClear.await(5, TimeUnit.SECONDS))
            }
            sequence.incrementAndGet()
        }
        JwtObserver::class.java.getDeclaredField("injectionSequence").apply {
            isAccessible = true
            set(observer, controlledSequence)
        }

        val staleClear = thread(name = "stale-clear") { oldCallback.invoke() }
        assertTrue(clearAtSequence.await(5, TimeUnit.SECONDS))
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("new")
        val restart = thread {
            observer.stopObserver()
            observer.startObserver()
        }
        try {
            restart.join(200)
        } finally {
            releaseClear.countDown()
            staleClear.join(5_000)
            restart.join(5_000)
        }
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockJsBridge.jwtMutation("new") }
    }

    @Test
    fun `startObserver injects token when fetch succeeds`() {
        val token = "header.payload.signature"
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken(token)

        JwtObserver().startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        verify { mockJsBridge.jwtMutation(token) }
        coVerify(exactly = 1) {
            mockAuthTokenManager.currentToken(AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS)
        }
    }

    @Test
    fun `reinjectCurrentToken delivers retained JWT after a profile mutation`() {
        val token = "header.payload.signature"
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken(token)
        val observer = JwtObserver()

        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        observer.reinjectCurrentToken()
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 2) { mockJsBridge.jwtMutation(token) }
    }

    @Test
    fun `startObserver injects empty string and logs debug when no provider registered`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } throws
            AuthTokenException.NoProviderRegistered

        JwtObserver().startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        verify { mockJsBridge.jwtMutation("") }
        verify { spyLog.debug(match { it.contains("Auth not enabled") }) }
    }

    @Test
    fun `startObserver injects empty string and logs warning when fetch fails`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } throws
            AuthTokenException.ValidationFailed("Malformed")

        JwtObserver().startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        verify { mockJsBridge.jwtMutation("") }
        verify { spyLog.warning(match { it.contains("Auth token fetch failed") }) }
    }

    @Test
    fun `stopObserver cancels in-flight fetch before injection`() {
        val tokenCompletion = CompletableDeferred<ValidatedToken>()
        coEvery { mockAuthTokenManager.currentToken(any()) } coAnswers { tokenCompletion.await() }

        val observer = JwtObserver()
        observer.startObserver()
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
        observer.startObserver()
        dispatcher.scheduler.runCurrent()

        // Second start before first fetch completes — should cancel the first job
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken(secondToken)
        observer.startObserver()
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
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("fresh")
        observer.startObserver()
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
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        refreshObserver.captured.invoke("refreshed.token") { true }

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
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        refreshObserver.captured.invoke(token) { true }

        verify(exactly = 1) { mockJsBridge.jwtMutation(token) }
    }

    @Test
    fun `initial fetch invalidated before queued ui delivery does not inject its token`() {
        val uiQueue = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers { uiQueue.add(firstArg()) }
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("stale")
        val observer = JwtObserver()

        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        every { mockAuthTokenManager.isCurrentToken(any()) } returns false
        uiQueue.forEach { it.invoke() }

        verify(inverse = true) { mockJsBridge.jwtMutation("stale") }
    }

    @Test
    fun `a refresh queued in a previous session does not inject into the new webview`() {
        // A proactive refresh dispatched during session 1 queues its UI callback, then the form is
        // closed and reopened (session 2) before that callback runs. The stale refresh must not
        // reach the fresh webview even though stopObserver/startObserver flipped `stopped` back.
        val uiQueue = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers { uiQueue.add(firstArg()) }

        val refreshObserver = captureRefreshObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("session-1")

        val observer = JwtObserver()
        observer.startObserver() // session 1
        dispatcher.scheduler.advanceUntilIdle() // session-1 fetch queues its inject
        refreshObserver.captured.invoke("stale-refresh") { true }

        observer.stopObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("session-2")
        observer.startObserver() // session 2 (fresh webview)
        dispatcher.scheduler.advanceUntilIdle() // session-2 fetch queues its inject

        uiQueue.forEach { it.invoke() }

        verify(inverse = true) { mockJsBridge.jwtMutation("stale-refresh") }
        verify(exactly = 1) { mockJsBridge.jwtMutation("session-2") }
    }

    @Test
    fun `refresh invalidated before queued ui delivery does not inject its token`() {
        val uiQueue = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers { uiQueue.add(firstArg()) }
        val refreshObserver = captureRefreshObserver()
        val invalidationObserver = captureInvalidationObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("initial")
        var current = true
        val observer = JwtObserver()

        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        uiQueue.forEach { it.invoke() }
        uiQueue.clear()
        clearMocks(mockJsBridge, answers = false)

        refreshObserver.captured.invoke("stale-refresh") { current }
        current = false
        invalidationObserver.captured.invoke()
        uiQueue.forEach { it.invoke() }

        verify(inverse = true) { mockJsBridge.jwtMutation("stale-refresh") }
        verify(exactly = 1) { mockJsBridge.jwtMutation("") }
    }

    @Test
    fun `invalidation during final refresh check fences injection and queues clear`() {
        val uiQueue = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers { uiQueue.add(firstArg()) }
        val refreshObserver = captureRefreshObserver()
        val invalidationObserver = captureInvalidationObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("initial")
        val observer = JwtObserver()

        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        uiQueue.forEach { it.invoke() }
        uiQueue.clear()
        clearMocks(mockJsBridge, answers = false)

        refreshObserver.captured.invoke("stale-refresh") {
            invalidationObserver.captured.invoke()
            true
        }
        while (uiQueue.isNotEmpty()) uiQueue.removeAt(0).invoke()

        verify(inverse = true) { mockJsBridge.jwtMutation("stale-refresh") }
        verify(exactly = 1) { mockJsBridge.jwtMutation("") }
    }

    @Test
    fun `invalidation after final refresh check clears the injected token`() {
        val uiQueue = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers { uiQueue.add(firstArg()) }
        val refreshObserver = captureRefreshObserver()
        val invalidationObserver = captureInvalidationObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("initial")
        val observer = JwtObserver()

        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        while (uiQueue.isNotEmpty()) uiQueue.removeAt(0).invoke()
        clearMocks(mockJsBridge, answers = false)
        every { mockJsBridge.jwtMutation(any()) } answers {
            if (firstArg<String>() == "refreshed") invalidationObserver.captured.invoke()
        }

        refreshObserver.captured.invoke("refreshed") { true }
        while (uiQueue.isNotEmpty()) uiQueue.removeAt(0).invoke()

        verifyOrder {
            mockJsBridge.jwtMutation("refreshed")
            mockJsBridge.jwtMutation("")
        }
    }

    @Test
    fun `unchanged token is re-injected into a fresh webview after a form restart`() {
        // JwtObserver is a shared instance across form sessions, but each session loads a new
        // webview with no JWT. The value-dedup must be scoped to the current webview: reopening a
        // form while the same token is still valid must re-deliver it rather than skip it.
        val token = "header.payload.signature"
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken(token)

        val observer = JwtObserver()
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        observer.stopObserver()

        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 2) { mockJsBridge.jwtMutation(token) }
    }

    @Test
    fun `refresh after stopObserver does not inject the stale token`() {
        val refreshObserver = captureRefreshObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("initial")

        val observer = JwtObserver()
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        val captured = refreshObserver.captured

        observer.stopObserver()
        captured.invoke("late.token") { true }

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
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle() // initial fetch queues its UI callback (index 0)

        refreshObserver.captured.invoke("fresh-refreshed") { true }

        // Run the refresh callback first, then the stale initial callback.
        uiQueue[1].invoke()
        uiQueue[0].invoke()

        verify(exactly = 1) { mockJsBridge.jwtMutation("fresh-refreshed") }
        verify(inverse = true) { mockJsBridge.jwtMutation("stale-cached") }
    }

    @Test
    fun `token arriving after the interactive attempt is still delivered`() {
        val refreshObserver = captureRefreshObserver()
        coEvery { mockAuthTokenManager.currentToken(any()) } throws AuthTokenException.TimedOut
        val observer = JwtObserver()

        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        refreshObserver.captured.invoke("late-token") { true }

        verify(exactly = 1) { mockJsBridge.jwtMutation("late-token") }
    }

    @Test
    fun `a failed initial fetch does not clobber a token delivered by an in-flight refresh`() {
        // The initial fetch is still suspended when a proactive refresh delivers a fresh token.
        // Because the fetch reserves its (lower) injection sequence at request time, its later
        // empty-string injection is dropped rather than overwriting the fresher refreshed token.
        val refreshObserver = captureRefreshObserver()
        val fetchCompletion = CompletableDeferred<ValidatedToken>()
        coEvery { mockAuthTokenManager.currentToken(any()) } coAnswers { fetchCompletion.await() }

        val observer = JwtObserver()
        observer.startObserver()
        dispatcher.scheduler.runCurrent() // initial fetch launched and suspended on currentToken

        // Refresh lands while the initial fetch is still in flight.
        refreshObserver.captured.invoke("fresh-refreshed") { true }

        // Initial fetch then fails — its callback would inject an empty JWT.
        fetchCompletion.completeExceptionally(AuthTokenException.ValidationFailed("Malformed"))
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockJsBridge.jwtMutation("fresh-refreshed") }
        verify(inverse = true) { mockJsBridge.jwtMutation("") }
    }

    @Test
    fun `restarting deregisters the prior refresh callback before registering the new one`() {
        val offObserver = slot<TokenRefreshObserver>()
        val onObservers = mutableListOf<TokenRefreshObserver>()
        every { mockAuthTokenManager.offTokenRefresh(capture(offObserver)) } just runs
        every { mockAuthTokenManager.onTokenRefresh(any()) } answers {
            onObservers += firstArg<TokenRefreshObserver>()
        }
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("initial")

        val observer = JwtObserver()
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        verifyOrder {
            mockAuthTokenManager.offTokenRefresh(any())
            mockAuthTokenManager.onTokenRefresh(any())
        }
        assertSame(onObservers.first(), offObserver.captured)
        assertNotSame(onObservers.first(), onObservers.last())
    }

    @Test
    fun `concurrent stop unregisters callback from an in-progress start`() {
        val registrationStarted = CountDownLatch(1)
        val releaseRegistration = CountDownLatch(1)
        val stopStarted = CountDownLatch(1)
        val callbacks = mutableListOf<TokenRefreshObserver>()
        every { mockAuthTokenManager.onTokenRefresh(any()) } answers {
            callbacks += firstArg<TokenRefreshObserver>()
            registrationStarted.countDown()
            assertTrue(releaseRegistration.await(5, TimeUnit.SECONDS))
        }
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("initial")
        val observer = JwtObserver()
        val start = thread { observer.startObserver() }
        assertTrue(registrationStarted.await(5, TimeUnit.SECONDS))
        val stop = thread {
            stopStarted.countDown()
            observer.stopObserver()
        }
        assertTrue(stopStarted.await(5, TimeUnit.SECONDS))
        stop.join(200)
        releaseRegistration.countDown()
        start.join(5_000)
        stop.join(5_000)

        assertTrue(!start.isAlive && !stop.isAlive)
        verify(exactly = 1) { mockAuthTokenManager.offTokenRefresh(callbacks.single()) }
    }
}

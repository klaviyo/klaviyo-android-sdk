package com.klaviyo.core.auth

import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KlaviyoAuthTokenManagerTest : BaseTest() {

    companion object {
        // staticClock returns TIME = 1234567890000ms → 1234567890s
        private const val NOW_SECONDS = 1234567890L
        private const val IAT_SECONDS = NOW_SECONDS - 60
        private const val EXP_SECONDS = NOW_SECONDS + 3600
    }

    @Test
    fun `currentToken throws NoProviderRegistered when no provider registered`() = runTest(
        dispatcher
    ) {
        val manager = KlaviyoAuthTokenManager()

        try {
            manager.currentToken()
            fail("Expected AuthTokenException.NoProviderRegistered")
        } catch (e: AuthTokenException.NoProviderRegistered) {
            assertEquals("No auth token provider registered", e.message)
        }
    }

    @Test
    fun `currentToken returns provider token on first fetch`() = runTest(dispatcher) {
        val token = makeJwt(EXP_SECONDS, IAT_SECONDS)
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(SuccessProvider(token))
        dispatcher.scheduler.advanceUntilIdle()

        val result = manager.currentToken()

        assertEquals(token, result.rawToken)
        assertEquals(EXP_SECONDS, result.expiresAtEpochSeconds)
        assertEquals(IAT_SECONDS, result.issuedAtEpochSeconds)
    }

    @Test
    fun `currentToken returns cached token without re-invoking provider`() = runTest(dispatcher) {
        val token = makeJwt(EXP_SECONDS, IAT_SECONDS)
        val provider = SuccessProvider(token)
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        // Eager fetch in registerProvider already invoked the provider once
        val callsAfterRegister = provider.callCount

        val result = manager.currentToken()

        assertEquals(token, result.rawToken)
        assertEquals(callsAfterRegister, provider.callCount)
    }

    @Test
    fun `registerProvider replaces previous provider and discards cached token`() = runTest(
        dispatcher
    ) {
        val firstToken = makeJwt(EXP_SECONDS, IAT_SECONDS)
        val secondToken = makeJwt(EXP_SECONDS + 1, IAT_SECONDS + 1)
        val firstProvider = SuccessProvider(firstToken)
        val secondProvider = SuccessProvider(secondToken)

        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(firstProvider)
        dispatcher.scheduler.advanceUntilIdle()
        manager.currentToken()

        manager.registerProvider(secondProvider)
        dispatcher.scheduler.advanceUntilIdle()

        val result = manager.currentToken()
        assertEquals(secondToken, result.rawToken)
    }

    @Test
    fun `registerProvider triggers eager fetch`() = runTest(dispatcher) {
        val token = makeJwt(EXP_SECONDS, IAT_SECONDS)
        val provider = SuccessProvider(token)
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            "registerProvider should have eagerly invoked the provider",
            provider.callCount >= 1
        )
    }

    @Test
    fun `registerProvider cancellation during eager fetch does not log warning`() = runTest(
        dispatcher
    ) {
        val provider = DeferredProvider()
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.runCurrent()
        manager.scope.cancel(CancellationException("teardown"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, provider.callCount)
        verify(exactly = 0) {
            spyLog.warning(any(), any<Throwable>())
        }
    }

    @Test
    fun `currentToken throws when provider invokes onFailure`() = runTest(dispatcher) {
        val failure = RuntimeException("network down")
        val manager = KlaviyoAuthTokenManager()
        // Use a provider that fails on the explicit currentToken() call. The eager fetch
        // in registerProvider will also fail, but its error is logged and not surfaced.
        manager.registerProvider(FailureProvider(failure))
        dispatcher.scheduler.advanceUntilIdle()

        try {
            manager.currentToken()
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("network down", e.message)
        }
    }

    @Test
    fun `currentToken throws ValidationFailed and logs error when returned jwt is malformed`() = runTest(
        dispatcher
    ) {
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(SuccessProvider("not-a-jwt"))
        dispatcher.scheduler.advanceUntilIdle()

        try {
            manager.currentToken()
            fail("Expected AuthTokenException.ValidationFailed")
        } catch (e: AuthTokenException.ValidationFailed) {
            // ValidationFailed.reason is non-null by construction; use it rather than
            // Throwable.message (which is platform-typed String?).
            assertTrue(
                "Expected non-empty validation reason, got: '${e.reason}'",
                e.reason.isNotEmpty()
            )
        }

        verify {
            spyLog.error(
                match { it.startsWith("Auth token validation failed:") },
                match { it is AuthTokenException.ValidationFailed }
            )
        }
    }

    @Test
    fun `currentToken throws ValidationFailed when returned jwt is expired on receipt`() = runTest(
        dispatcher
    ) {
        // exp within leeway of NOW → ExpiredOnReceipt
        val expired = makeJwt(NOW_SECONDS, IAT_SECONDS)
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(SuccessProvider(expired))
        dispatcher.scheduler.advanceUntilIdle()

        try {
            manager.currentToken()
            fail("Expected AuthTokenException.ValidationFailed")
        } catch (e: AuthTokenException.ValidationFailed) {
            assertEquals("ExpiredOnReceipt", e.reason)
        }
    }

    @Test
    fun `currentToken does not invoke onFailure-and-then-onSuccess twice`() = runTest(dispatcher) {
        // Late callback resolution should be harmlessly ignored (isActive guard).
        val token = makeJwt(EXP_SECONDS, IAT_SECONDS)
        val provider = AuthTokenProvider { callback ->
            callback.onSuccess(token)
            // Simulate buggy host calling back twice — should not throw.
            callback.onSuccess(token)
            callback.onFailure(RuntimeException("late failure"))
        }
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        val result = manager.currentToken()
        assertEquals(token, result.rawToken)
    }

    // MARK: - MAGE-628: Deduplication and Timeouts

    @Test
    fun `concurrent callers share a single provider invocation`() = runTest(dispatcher) {
        val token = makeJwt(EXP_SECONDS, IAT_SECONDS)
        val provider = ResolvableProvider()
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        // Hold off on advancing — keep the eager fetch pending so all callers race the same deferred.

        val results = mutableListOf<ValidatedToken>()
        val jobs = (1..5).map {
            launch { results.add(manager.currentToken()) }
        }

        // Let all coroutines start and reach their await() on the shared deferred.
        dispatcher.scheduler.runCurrent()

        // One provider resolution satisfies the deferred and all awaiting callers.
        provider.resolve(token)
        dispatcher.scheduler.advanceUntilIdle()
        jobs.forEach { it.join() }

        assertEquals(1, provider.callCount)
        assertEquals(5, results.size)
        assertTrue(results.all { it.rawToken == token })
    }

    @Test
    fun `currentToken throws TimedOut when provider does not respond within timeout`() = runTest(
        dispatcher
    ) {
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(DeferredProvider())
        dispatcher.scheduler.runCurrent() // start eager fetch (also waiting on provider)

        var caught: AuthTokenException.TimedOut? = null
        val job = launch {
            try {
                manager.currentToken(timeoutMs = 100)
            } catch (e: AuthTokenException.TimedOut) {
                caught = e
            }
        }
        dispatcher.scheduler.runCurrent()
        dispatcher.scheduler.advanceTimeBy(101)
        dispatcher.scheduler.runCurrent()
        job.join()

        assertNotNull("Expected TimedOut to be thrown", caught)
        verify {
            spyLog.error(
                match { it.contains("timed out", ignoreCase = true) },
                any<AuthTokenException.TimedOut>()
            )
        }
    }

    @Test
    fun `timeout does not cancel underlying fetch so later callers still benefit`() = runTest(
        dispatcher
    ) {
        val token = makeJwt(EXP_SECONDS, IAT_SECONDS)
        val provider = ResolvableProvider()
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.runCurrent() // eager fetch starts and suspends on provider

        // First caller times out after 100ms.
        var timedOut = false
        val firstJob = launch {
            try {
                manager.currentToken(timeoutMs = 100)
            } catch (_: AuthTokenException.TimedOut) {
                timedOut = true
            }
        }
        dispatcher.scheduler.runCurrent()
        dispatcher.scheduler.advanceTimeBy(101)
        dispatcher.scheduler.runCurrent()
        firstJob.join()
        assertTrue("First caller should have timed out", timedOut)

        // Underlying fetch is still alive — resolve the provider now.
        provider.resolve(token)
        dispatcher.scheduler.advanceUntilIdle()

        // Second caller gets the token from cache populated by the completed underlying fetch.
        val result = manager.currentToken()
        assertEquals(token, result.rawToken)
        assertEquals(1, provider.callCount)
    }

    @Test
    fun `failure clears in-flight slot so next call re-invokes provider`() = runTest(dispatcher) {
        val failure = RuntimeException("fetch failed")
        val provider = CountingFailureProvider(failure)
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle() // eager fetch fails (callCount = 1)

        try { manager.currentToken() } catch (_: Exception) {}
        val countAfterFirst = provider.callCount // eager + 1 explicit

        try { manager.currentToken() } catch (_: Exception) {}

        // Each call after a failure must re-invoke the provider (slot was cleared by invokeOnCompletion).
        assertEquals(countAfterFirst + 1, provider.callCount)
    }

    @Test
    fun `provider swap cancels in-flight fetch so stale token cannot poison cache`() = runTest(
        dispatcher
    ) {
        val staleToken = makeJwt(EXP_SECONDS, IAT_SECONDS)
        val freshToken = makeJwt(EXP_SECONDS + 100, IAT_SECONDS + 100)
        val providerA = ResolvableProvider()
        val providerB = SuccessProvider(freshToken)
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(providerA)
        dispatcher.scheduler.runCurrent() // eager fetch for A starts, awaits provider

        // Swap provider while A's fetch is in-flight; B's eager fetch resolves immediately.
        manager.registerProvider(providerB)
        dispatcher.scheduler.advanceUntilIdle() // B's eager fetch completes → cache = freshToken

        // A calls back late — its continuation is inactive (deferred was cancelled on swap).
        providerA.resolve(staleToken)
        dispatcher.scheduler.advanceUntilIdle()

        // Cache should hold freshToken, not staleToken.
        val result = manager.currentToken()
        assertEquals(freshToken, result.rawToken)
    }

    @Test
    fun `provider swap does not write stale token when callback fires before cancellation`() = runTest(
        dispatcher
    ) {
        // Regression test for the uncontended-mutex cancellation gap:
        // If invokeProvider() already returned a value before the deferred is cancelled,
        // ensureActive() in doFetch() must throw CancellationException before the cache write —
        // even though mutex.withLock would acquire an uncontended lock without suspending.
        val staleToken = makeJwt(EXP_SECONDS, IAT_SECONDS)
        val freshToken = makeJwt(EXP_SECONDS + 100, IAT_SECONDS + 100)
        val providerA = ResolvableProvider()
        val providerB = SuccessProvider(freshToken)
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(providerA)
        dispatcher.scheduler.runCurrent() // A's eager fetch starts and suspends on provider

        // Resolve A's callback now — this schedules A's coroutine to resume with staleToken.
        // A's doFetch() has not yet run past invokeProvider().
        providerA.resolve(staleToken)

        // Swap to B *before* the scheduler runs A's resumed coroutine. Without ensureActive(),
        // A's doFetch() would proceed through validateOrThrow and write staleToken to the cache
        // on an uncontended mutex.withLock (no suspension → no cancellation check there).
        manager.registerProvider(providerB)
        dispatcher.scheduler.advanceUntilIdle() // A hits ensureActive() and aborts; B completes

        val result = manager.currentToken()
        assertEquals(freshToken, result.rawToken)
    }

    // MARK: - Helpers

    private class SuccessProvider(private val jwt: String) : AuthTokenProvider {
        var callCount: Int = 0
            private set

        override fun fetchToken(callback: AuthTokenProvider.Callback) {
            callCount++
            callback.onSuccess(jwt)
        }
    }

    private class FailureProvider(private val error: Throwable) : AuthTokenProvider {
        override fun fetchToken(callback: AuthTokenProvider.Callback) {
            callback.onFailure(error)
        }
    }

    private class DeferredProvider : AuthTokenProvider {
        var callCount: Int = 0
            private set

        override fun fetchToken(callback: AuthTokenProvider.Callback) {
            callCount++
        }
    }

    private fun makeJwt(expSeconds: Long, iatSeconds: Long): String {
        val header = JSONObject(mapOf("alg" to "HS256", "typ" to "JWT"))
        val payload = JSONObject(
            mapOf(
                "exp" to expSeconds.toDouble(),
                "iat" to iatSeconds.toDouble()
            )
        )
        val headerSegment = base64UrlEncode(header.toString().toByteArray())
        val payloadSegment = base64UrlEncode(payload.toString().toByteArray())
        return "$headerSegment.$payloadSegment.signature"
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /**
     * Provider that captures callbacks so tests can resolve them manually, enabling
     * deterministic control over when the provider "responds". Callbacks are queued so
     * concurrent invocations (e.g. eager fetch + explicit caller) can each be resolved
     * independently without the second call clobbering the first.
     */
    private class ResolvableProvider : AuthTokenProvider {
        var callCount: Int = 0
            private set
        private val pendingCallbacks = ArrayDeque<AuthTokenProvider.Callback>()

        override fun fetchToken(callback: AuthTokenProvider.Callback) {
            callCount++
            pendingCallbacks.addLast(callback)
        }

        fun resolve(jwt: String) = pendingCallbacks.removeFirstOrNull()?.onSuccess(jwt)
        fun reject(error: Throwable) = pendingCallbacks.removeFirstOrNull()?.onFailure(error)
    }

    /**
     * Failure provider that exposes an invocation counter for asserting that the in-flight slot
     * is cleared on failure (so each new call re-invokes the provider).
     */
    private class CountingFailureProvider(private val error: Throwable) : AuthTokenProvider {
        var callCount: Int = 0
            private set

        override fun fetchToken(callback: AuthTokenProvider.Callback) {
            callCount++
            callback.onFailure(error)
        }
    }
}

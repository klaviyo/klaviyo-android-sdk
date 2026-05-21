package com.klaviyo.analytics.auth

import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
    fun `currentToken throws when no provider registered`() = runTest {
        val manager = KlaviyoAuthTokenManager()

        try {
            manager.currentToken()
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("No auth token provider registered", e.message)
        }
    }

    @Test
    fun `currentToken returns provider token on first fetch`() = runTest {
        val token = makeJwt(EXP_SECONDS, IAT_SECONDS)
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(SuccessProvider(token))
        dispatcher.scheduler.advanceUntilIdle()

        val result = manager.currentToken()

        assertEquals(token, result)
    }

    @Test
    fun `currentToken returns cached token without re-invoking provider`() = runTest {
        val token = makeJwt(EXP_SECONDS, IAT_SECONDS)
        val provider = SuccessProvider(token)
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        // Eager fetch in registerProvider already invoked the provider once
        val callsAfterRegister = provider.callCount

        val result = manager.currentToken()

        assertEquals(token, result)
        assertEquals(callsAfterRegister, provider.callCount)
    }

    @Test
    fun `registerProvider replaces previous provider and discards cached token`() = runTest {
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
        assertEquals(secondToken, result)
    }

    @Test
    fun `registerProvider triggers eager fetch`() = runTest {
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
    fun `registerProvider cancellation during eager fetch does not log warning`() = runTest {
        val provider = DeferredProvider()
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.runCurrent()
        manager.coroutineScope.cancel(CancellationException("teardown"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, provider.callCount)
        verify(exactly = 0) {
            spyLog.warning("Eager auth token fetch failed", any<Throwable?>())
        }
    }

    @Test
    fun `currentToken throws when provider invokes onFailure`() = runTest {
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
    fun `currentToken throws and logs error when returned jwt is malformed`() = runTest {
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(SuccessProvider("not-a-jwt"))
        dispatcher.scheduler.advanceUntilIdle()

        try {
            manager.currentToken()
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(
                "Expected validation failure message, got: ${e.message}",
                e.message?.startsWith("Auth token validation failed:") == true
            )
        }

        verify {
            spyLog.error(
                match { it.startsWith("Auth token validation failed:") },
                match { it is IllegalStateException }
            )
        }
    }

    @Test
    fun `currentToken throws when returned jwt is expired on receipt`() = runTest {
        // exp within leeway of NOW → ExpiredOnReceipt
        val expired = makeJwt(NOW_SECONDS, IAT_SECONDS)
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(SuccessProvider(expired))
        dispatcher.scheduler.advanceUntilIdle()

        try {
            manager.currentToken()
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(
                "Expected ExpiredOnReceipt failure, got: ${e.message}",
                e.message?.contains("ExpiredOnReceipt") == true
            )
        }
    }

    @Test
    fun `currentToken does not invoke onFailure-and-then-onSuccess twice`() = runTest {
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
        assertEquals(token, result)
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
}

package com.klaviyo.core.auth

import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import java.net.UnknownHostException
import java.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Tests for [KlaviyoAuthTokenManager.refreshRejectedToken], the handler for the webview's
 * `refreshJwt` signal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KlaviyoAuthTokenManagerRejectedTokenTest : BaseTest() {

    companion object {
        private const val NOW_SECONDS = TIME / 1000L
        private const val IAT_SECONDS = NOW_SECONDS - 60
        private const val EXP_SECONDS = NOW_SECONDS + 3600
    }

    private val rejectedJwt = makeJwt(EXP_SECONDS, IAT_SECONDS)
    private val replacementJwt = makeJwt(EXP_SECONDS + 100, IAT_SECONDS + 100)
    private val nextProfileJwt = makeJwt(EXP_SECONDS + 200, IAT_SECONDS + 200)

    private val provider = ResolvableProvider()
    private val received = mutableListOf<String>()
    private val network = FakeNetworkMonitor()

    @Before
    override fun setup() {
        super.setup()
        every { Registry.networkMonitor } returns network
    }

    /** Registers [provider] and resolves its eager fetch with [rejectedJwt]. */
    private fun managerWithDeliveredToken(): KlaviyoAuthTokenManager =
        identifiedAuthTokenManager().apply {
            onTokenRefresh { received.add(it) }
            registerProvider(provider)
            dispatcher.scheduler.runCurrent()
            provider.resolve(rejectedJwt)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf(rejectedJwt), received)
            assertEquals(1, provider.callCount)
        }

    @Test
    fun `rejected token refresh does not invoke the provider while not identified`() =
        runTest(dispatcher) {
            val manager = KlaviyoAuthTokenManager()
            manager.registerProvider(provider)
            dispatcher.scheduler.advanceUntilIdle()

            manager.refreshRejectedToken()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(0, provider.callCount)
            assertTrue(received.isEmpty())
        }

    @Test
    fun `rejected token refresh after de-identifying does not invoke the provider`() =
        runTest(dispatcher) {
            val manager = managerWithDeliveredToken()

            manager.setIdentified(false)
            manager.refreshRejectedToken()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, provider.callCount)
            assertFalse(manager.isCurrentToken(rejectedJwt))
        }

    @Test
    fun `rejected token is discarded and one replacement is delivered`() = runTest(dispatcher) {
        val manager = managerWithDeliveredToken()

        manager.refreshRejectedToken()
        dispatcher.scheduler.runCurrent()
        assertEquals(2, provider.callCount)

        provider.resolve(replacementJwt)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(rejectedJwt, replacementJwt), received)
        assertEquals(replacementJwt, manager.currentToken().rawToken)
        assertEquals(2, provider.callCount)
    }

    @Test
    fun `caller during the refresh waits for the replacement instead of the rejected token`() =
        runTest(dispatcher) {
            val manager = managerWithDeliveredToken()

            manager.refreshRejectedToken()
            val caller = async { manager.currentToken() }
            dispatcher.scheduler.runCurrent()
            assertFalse(caller.isCompleted)

            provider.resolve(replacementJwt)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(replacementJwt, caller.await().rawToken)
            assertEquals(2, provider.callCount)
        }

    @Test
    fun `pending delivery of the rejected token stops at the refresh`() = runTest(dispatcher) {
        val manager = identifiedAuthTokenManager()
        manager.onTokenRefresh { jwt -> if (jwt == rejectedJwt) manager.refreshRejectedToken() }
        manager.onTokenRefresh { received.add(it) }
        manager.registerProvider(provider)
        dispatcher.scheduler.runCurrent()

        provider.resolve(rejectedJwt)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(received.isEmpty())
        assertEquals(2, provider.callCount)

        provider.resolve(replacementJwt)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(replacementJwt), received)
    }

    @Test
    fun `overlapping refresh requests share one provider call`() = runTest(dispatcher) {
        val manager = managerWithDeliveredToken()

        manager.refreshRejectedToken()
        manager.refreshRejectedToken()
        dispatcher.scheduler.runCurrent()
        manager.refreshRejectedToken()
        dispatcher.scheduler.runCurrent()

        assertEquals(2, provider.callCount)

        provider.resolve(replacementJwt)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(rejectedJwt, replacementJwt), received)
        assertEquals(2, provider.callCount)
    }

    @Test
    fun `refresh joins a fetch that is already in flight`() = runTest(dispatcher) {
        val manager = identifiedAuthTokenManager()
        manager.onTokenRefresh { received.add(it) }
        manager.registerProvider(provider)
        dispatcher.scheduler.runCurrent()

        manager.refreshRejectedToken()
        dispatcher.scheduler.runCurrent()
        assertEquals(1, provider.callCount)

        provider.resolve(replacementJwt)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(replacementJwt), received)
        assertEquals(1, provider.callCount)
    }

    @Test
    fun `refresh without a provider does nothing`() = runTest(dispatcher) {
        val manager = identifiedAuthTokenManager()
        manager.onTokenRefresh { received.add(it) }

        manager.refreshRejectedToken()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(received.isEmpty())
        assertThrowsNoProvider(manager)
        verify(exactly = 0) { spyLog.error(any(), any<Throwable>()) }
    }

    @Test
    fun `failed replacement delivers nothing and the next caller fetches again`() =
        runTest(dispatcher) {
            val manager = managerWithDeliveredToken()

            manager.refreshRejectedToken()
            dispatcher.scheduler.runCurrent()
            provider.reject(IllegalStateException("host failure"))
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(rejectedJwt), received)
            assertEquals(2, provider.callCount)
            verify(exactly = 0) { spyLog.error(any(), any<Throwable>()) }

            val caller = async { manager.currentToken() }
            dispatcher.scheduler.runCurrent()
            assertEquals(3, provider.callCount)

            provider.resolve(replacementJwt)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(replacementJwt, caller.await().rawToken)
            assertEquals(listOf(rejectedJwt, replacementJwt), received)
        }

    @Test
    fun `provider removal mid-refresh drops the replacement without notifying observers`() =
        runTest(dispatcher) {
            val manager = managerWithDeliveredToken()

            manager.refreshRejectedToken()
            dispatcher.scheduler.runCurrent()
            manager.unregisterProvider()
            dispatcher.scheduler.runCurrent()
            provider.resolve(replacementJwt)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(rejectedJwt), received)
            assertEquals(2, provider.callCount)
            assertThrowsNoProvider(manager)
        }

    @Test
    fun `replacement landing after a profile change is not delivered`() = runTest(dispatcher) {
        val manager = managerWithDeliveredToken()

        manager.refreshRejectedToken()
        dispatcher.scheduler.runCurrent()
        val generation = manager.invalidate()
        provider.resolve(replacementJwt)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(rejectedJwt), received)

        manager.clearTokenState(generation)
        val caller = async { manager.currentToken() }
        dispatcher.scheduler.runCurrent()
        assertEquals(3, provider.callCount)
        provider.resolve(nextProfileJwt)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(nextProfileJwt, caller.await().rawToken)
        assertEquals(listOf(rejectedJwt, nextProfileJwt), received)
    }

    @Test
    fun `refresh during a pending profile reset is skipped`() = runTest(dispatcher) {
        val manager = managerWithDeliveredToken()

        val generation = manager.invalidate()
        manager.refreshRejectedToken()
        manager.setIdentified(false)
        dispatcher.scheduler.advanceUntilIdle()
        manager.clearTokenState(generation)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, provider.callCount)
        assertEquals(listOf(rejectedJwt), received)
        verify { spyLog.debug(match { it.contains("skipped") }) }
    }

    @Test
    fun `refresh during a pending profile replacement is skipped and the fence prewarms once`() =
        runTest(dispatcher) {
            val manager = managerWithDeliveredToken()

            val generation = manager.invalidate()
            manager.refreshRejectedToken()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, provider.callCount)
            manager.clearTokenState(generation)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, provider.callCount)
            assertEquals(listOf(rejectedJwt), received)
            verify { spyLog.debug(match { it.contains("skipped") }) }
        }

    @Test
    fun `refresh queued before a provider replace does not reach either provider twice`() =
        runTest(dispatcher) {
            val manager = managerWithDeliveredToken()
            val replacementProvider = ResolvableProvider()

            manager.refreshRejectedToken()
            manager.registerProvider(replacementProvider)
            dispatcher.scheduler.runCurrent()

            assertEquals(1, provider.callCount)
            assertEquals(1, replacementProvider.callCount)

            replacementProvider.resolve(replacementJwt)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(rejectedJwt, replacementJwt), received)
            assertEquals(replacementJwt, manager.currentToken().rawToken)
            assertEquals(1, provider.callCount)
            assertEquals(1, replacementProvider.callCount)
        }

    @Test
    fun `refresh queued after a provider replace joins the new provider's fetch`() =
        runTest(dispatcher) {
            val manager = managerWithDeliveredToken()
            val replacementProvider = ResolvableProvider()

            manager.registerProvider(replacementProvider)
            manager.refreshRejectedToken()
            dispatcher.scheduler.runCurrent()

            assertEquals(1, replacementProvider.callCount)

            replacementProvider.resolve(replacementJwt)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(rejectedJwt, replacementJwt), received)
            assertEquals(replacementJwt, manager.currentToken().rawToken)
            assertEquals(1, provider.callCount)
            assertEquals(1, replacementProvider.callCount)
        }

    @Test
    fun `network-failed replacement is retried on each connectivity event until it succeeds`() =
        runTest(dispatcher) {
            val manager = managerWithDeliveredToken()

            manager.refreshRejectedToken()
            dispatcher.scheduler.runCurrent()
            assertEquals(2, provider.callCount)
            provider.reject(UnknownHostException("offline"))
            dispatcher.scheduler.runCurrent()
            assertEquals(2, provider.callCount)
            assertNotNull(manager.connectivityWaitJob())

            network.simulateConnected(isConnected = true)
            dispatcher.scheduler.runCurrent()
            assertEquals(3, provider.callCount)

            provider.reject(UnknownHostException("still offline"))
            dispatcher.scheduler.runCurrent()
            assertEquals(3, provider.callCount)
            assertNotNull(manager.connectivityWaitJob())

            network.simulateConnected(isConnected = true)
            dispatcher.scheduler.runCurrent()
            assertEquals(4, provider.callCount)

            provider.resolve(replacementJwt)
            dispatcher.scheduler.advanceUntilIdle()
            network.simulateConnected(isConnected = true)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(rejectedJwt, replacementJwt), received)
            assertEquals(replacementJwt, manager.currentToken().rawToken)
            assertEquals(4, provider.callCount)
            assertNull(manager.connectivityWaitJob())
            assertEquals(0, network.observerCount())
        }

    @Test
    fun `network failure of the replacement while connected retries once then waits`() =
        runTest(dispatcher) {
            network.connected = true
            val manager = managerWithDeliveredToken()

            manager.refreshRejectedToken()
            dispatcher.scheduler.runCurrent()
            assertEquals(2, provider.callCount)
            provider.reject(UnknownHostException("endpoint down"))
            dispatcher.scheduler.runCurrent()
            assertEquals(3, provider.callCount)

            provider.reject(UnknownHostException("still down"))
            dispatcher.scheduler.runCurrent()
            assertEquals(3, provider.callCount)
            assertNotNull(manager.connectivityWaitJob())

            network.simulateConnected(isConnected = true)
            dispatcher.scheduler.runCurrent()
            assertEquals(4, provider.callCount)

            provider.resolve(replacementJwt)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(rejectedJwt, replacementJwt), received)
            assertEquals(4, provider.callCount)
            assertNull(manager.connectivityWaitJob())
        }

    @Test
    fun `rejection during an in-flight refresh keeps its connectivity recovery`() =
        runTest(dispatcher) {
            val manager = managerWithDeliveredToken()
            val timer = staticClock.scheduledTasks.single()
            staticClock.execute(timer.time - staticClock.time)
            dispatcher.scheduler.runCurrent()
            assertEquals(2, provider.callCount)

            manager.refreshRejectedToken()
            dispatcher.scheduler.runCurrent()
            assertEquals(2, provider.callCount)

            provider.reject(UnknownHostException("offline"))
            dispatcher.scheduler.runCurrent()
            assertEquals(2, provider.callCount)
            assertNotNull(manager.connectivityWaitJob())

            network.simulateConnected(isConnected = true)
            dispatcher.scheduler.runCurrent()
            assertEquals(3, provider.callCount)

            provider.resolve(replacementJwt)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(rejectedJwt, replacementJwt), received)
            assertEquals(3, provider.callCount)
        }

    @Test
    fun `rejected token's scheduled refresh does not fire after a failed replacement`() =
        runTest(dispatcher) {
            val manager = managerWithDeliveredToken()
            assertEquals(1, staticClock.scheduledTasks.size)

            manager.refreshRejectedToken()
            dispatcher.scheduler.runCurrent()
            assertTrue(staticClock.scheduledTasks.isEmpty())
            provider.reject(IllegalStateException("host failure"))
            dispatcher.scheduler.advanceUntilIdle()

            staticClock.execute(EXP_SECONDS * 1000L - TIME)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, provider.callCount)
            assertEquals(listOf(rejectedJwt), received)
        }

    @Test
    fun `foreground after a failed replacement logs that no token is cached`() =
        runTest(dispatcher) {
            val lifecycleObserver = slot<ActivityObserver>()
            every { mockLifecycleMonitor.onActivityEvent(capture(lifecycleObserver)) } returns Unit
            managerWithDeliveredToken().refreshRejectedToken()
            dispatcher.scheduler.runCurrent()
            provider.reject(IllegalStateException("host failure"))
            dispatcher.scheduler.advanceUntilIdle()

            lifecycleObserver.captured.invoke(ActivityEvent.FirstStarted(mockActivity))
            dispatcher.scheduler.advanceUntilIdle()

            verify { spyLog.info(match { it.contains("case=no-cached-token") }) }
            verify(inverse = true) { spyLog.info(match { it.contains("case=still-valid") }) }
            assertEquals(2, provider.callCount)
        }

    @Test
    fun `successful replacement schedules a refresh for the new token`() = runTest(dispatcher) {
        val manager = managerWithDeliveredToken()

        manager.refreshRejectedToken()
        dispatcher.scheduler.runCurrent()
        provider.resolve(replacementJwt)
        dispatcher.scheduler.advanceUntilIdle()

        val replacement = manager.currentToken()
        val target = KlaviyoAuthTokenManager.computeRefreshTarget(replacement, TIME)
        assertEquals(listOf(target), staticClock.scheduledTasks.map { it.time })

        staticClock.execute(target - TIME)
        dispatcher.scheduler.runCurrent()

        assertEquals(3, provider.callCount)
    }

    private suspend fun assertThrowsNoProvider(manager: KlaviyoAuthTokenManager) {
        try {
            manager.currentToken()
            fail("Expected AuthTokenException.NoProviderRegistered")
        } catch (_: AuthTokenException.NoProviderRegistered) {
        }
    }

    private fun makeJwt(expSeconds: Long, iatSeconds: Long): String {
        val header = JSONObject(mapOf("alg" to "HS256", "typ" to "JWT"))
        val payload = JSONObject(
            mapOf("exp" to expSeconds.toDouble(), "iat" to iatSeconds.toDouble())
        )
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val h = encoder.encodeToString(header.toString().toByteArray())
        val p = encoder.encodeToString(payload.toString().toByteArray())
        return "$h.$p.c2lnbmF0dXJl"
    }

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
}

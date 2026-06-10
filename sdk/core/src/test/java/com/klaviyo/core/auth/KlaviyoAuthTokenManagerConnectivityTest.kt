package com.klaviyo.core.auth

import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.core.networking.NetworkMonitor
import com.klaviyo.core.networking.NetworkObserver
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests for the connectivity-driven refresh retry path introduced in MAGE-684.
 *
 * The controllable [FakeNetworkMonitor] lets tests drive synthetic connectivity transitions
 * without involving real system APIs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KlaviyoAuthTokenManagerConnectivityTest : BaseTest() {

    companion object {
        private const val NOW_SECONDS = TIME / 1000L
        private const val IAT_SECONDS = NOW_SECONDS - 60
        private const val EXP_SECONDS = NOW_SECONDS + 3600
    }

    private lateinit var fakeNetworkMonitor: FakeNetworkMonitor
    private val observerSlot = slot<ActivityObserver>()

    @Before
    override fun setup() {
        super.setup()
        fakeNetworkMonitor = FakeNetworkMonitor()
        every { Registry.networkMonitor } returns fakeNetworkMonitor
        every { mockLifecycleMonitor.onActivityEvent(capture(observerSlot)) } returns Unit
    }

    // MARK: - Helpers

    private fun makeJwt(expSeconds: Long = EXP_SECONDS, iatSeconds: Long = IAT_SECONDS): String {
        val header = JSONObject(mapOf("alg" to "HS256", "typ" to "JWT"))
        val payload = JSONObject(
            mapOf("exp" to expSeconds.toDouble(), "iat" to iatSeconds.toDouble())
        )
        val h = base64UrlEncode(header.toString().toByteArray())
        val p = base64UrlEncode(payload.toString().toByteArray())
        return "$h.$p.signature"
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    // MARK: - Retry fires after reconnect

    @Test
    fun `connectivity retry fires after network comes back online after IOException`() = runTest(
        dispatcher
    ) {
        val initialToken = makeJwt()
        val retryToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(initialToken),
                    Result.failure(IOException("network down")),
                    Result.success(retryToken)
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("initial eager fetch", 1, provider.callCount)

        // Fire the scheduled refresh — it fails with a network error
        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("refresh attempt failed", 2, provider.callCount)

        // connectivityWaitJob should be armed
        assertNotNull(
            "connectivityWaitJob should be armed after network failure",
            manager.connectivityWaitJob
        )
        verify { spyLog.info(match { it.contains("waiting for connectivity") }) }

        // Simulate connectivity restored
        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "connectivity retry should invoke provider a third time",
            3,
            provider.callCount
        )
        verify { spyLog.info(match { it.contains("connectivity restored") }) }
    }

    @Test
    fun `connectivity retry fires after UnknownHostException`() = runTest(dispatcher) {
        val initialToken = makeJwt()
        val retryToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(initialToken),
                    Result.failure(UnknownHostException("host unknown")),
                    Result.success(retryToken)
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, provider.callCount)

        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("retry after UnknownHostException", 3, provider.callCount)
    }

    @Test
    fun `connectivity retry fires after SocketTimeoutException`() = runTest(dispatcher) {
        val initialToken = makeJwt()
        val retryToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(initialToken),
                    Result.failure(SocketTimeoutException("timed out")),
                    Result.success(retryToken)
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()

        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("retry after SocketTimeoutException", 3, provider.callCount)
    }

    @Test
    fun `connectivity retry fires after ConnectException`() = runTest(dispatcher) {
        val initialToken = makeJwt()
        val retryToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(initialToken),
                    Result.failure(ConnectException("connection refused")),
                    Result.success(retryToken)
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()

        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("retry after ConnectException", 3, provider.callCount)
    }

    @Test
    fun `connectivity wait job is not armed when connectivity notification is offline`() = runTest(
        dispatcher
    ) {
        val initialToken = makeJwt()
        val retryToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(initialToken),
                    Result.failure(IOException("network down")),
                    Result.success(retryToken)
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("initial refresh failed", 2, provider.callCount)

        // Simulate still offline — should not trigger retry
        fakeNetworkMonitor.simulateConnected(isConnected = false)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("offline notification must not trigger retry", 2, provider.callCount)

        // Simulate connected — should trigger retry
        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("connected notification should trigger retry", 3, provider.callCount)
    }

    // MARK: - At-most-one job invariant

    @Test
    fun `rapid flap cancels existing connectivity wait job before arming new one`() = runTest(
        dispatcher
    ) {
        val initialToken = makeJwt()
        // Scripted to fail multiple times with network errors
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(initialToken),
                    Result.failure(IOException("flap 1")),
                    Result.failure(IOException("flap 2")),
                    Result.failure(IOException("flap 3")),
                    Result.success(makeJwt(EXP_SECONDS + 100, IAT_SECONDS + 100))
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        // Fire scheduled refresh — fails, arms connectivityWaitJob
        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, provider.callCount)

        val firstJob = manager.connectivityWaitJob
        assertNotNull("first job should be armed", firstJob)

        // Simulate connectivity restored → retry fires → fails again → re-arms
        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("first retry fired", 3, provider.callCount)

        // Re-armed after second failure
        val secondJob = manager.connectivityWaitJob
        assertNotNull("second job should be re-armed", secondJob)

        // First job should be a different (cancelled) instance from the second
        assertEquals("second job should be active", false, secondJob!!.isCancelled)

        // Only one observer should be registered in the network monitor at any time
        // (first was de-registered on its completion, second is the only active one)
        assertEquals(
            "only one network observer should be registered after re-arm",
            1,
            fakeNetworkMonitor.observerCount()
        )
    }

    @Test
    fun `registering new provider while connectivity wait is armed cancels the job`() = runTest(
        dispatcher
    ) {
        val initialToken = makeJwt()
        val newToken = makeJwt(EXP_SECONDS + 200, IAT_SECONDS + 200)
        val firstProvider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(initialToken),
                    Result.failure(IOException("network down"))
                )
            )
        )
        val secondProvider = CountingSuccessProvider(newToken)
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(firstProvider)
        dispatcher.scheduler.advanceUntilIdle()

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()
        val armedJob = manager.connectivityWaitJob
        assertNotNull("connectivityWaitJob should be armed", armedJob)

        // Register new provider — should cancel the pending connectivity wait
        manager.registerProvider(secondProvider)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(
            "connectivityWaitJob should be cleared after registerProvider",
            manager.connectivityWaitJob
        )
        assertEquals("cancelled job should be inactive", true, armedJob!!.isCancelled)
        assertEquals(
            "no network observer should remain after provider swap",
            0,
            fakeNetworkMonitor.observerCount()
        )
    }

    // MARK: - Non-network failures do not arm the retry

    @Test
    fun `non-network exception does not arm connectivity wait job`() = runTest(dispatcher) {
        val initialToken = makeJwt()
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(initialToken),
                    Result.failure(RuntimeException("http 500"))
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("RuntimeException must not arm connectivityWaitJob", manager.connectivityWaitJob)
        assertEquals(
            "no observer registered for non-network failure",
            0,
            fakeNetworkMonitor.observerCount()
        )
        verify(inverse = true) {
            spyLog.info(match { it.contains("waiting for connectivity") })
        }
    }

    @Test
    fun `validation failure does not arm connectivity wait job`() = runTest(dispatcher) {
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(makeJwt()),
                    Result.success("not-a-valid-jwt") // will fail validation
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("ValidationFailed must not arm connectivityWaitJob", manager.connectivityWaitJob)
        assertEquals(0, fakeNetworkMonitor.observerCount())
    }

    // MARK: - clearTokenState cancels the connectivity wait job

    @Test
    fun `clearTokenState cancels and clears connectivity wait job`() = runTest(dispatcher) {
        val initialToken = makeJwt()
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(initialToken),
                    Result.failure(IOException("network down"))
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()

        val armedJob = manager.connectivityWaitJob
        assertNotNull("job must be armed before clear", armedJob)

        // Clear token state (simulates logout / resetProfile)
        manager.clearTokenState()

        assertNull(
            "connectivityWaitJob must be null after clearTokenState",
            manager.connectivityWaitJob
        )
        assertEquals("armed job must be cancelled", true, armedJob!!.isCancelled)
        assertEquals(
            "network observer should be de-registered on cancellation",
            0,
            fakeNetworkMonitor.observerCount()
        )

        // Subsequent connectivity event should NOT trigger a retry
        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("no retry after clearTokenState", 2, provider.callCount)
    }

    @Test
    fun `clearTokenState with stale generation does not cancel connectivity wait job`() = runTest(
        dispatcher
    ) {
        val initialToken = makeJwt()
        val newToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
        val firstProvider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(initialToken),
                    Result.failure(IOException("network down"))
                )
            )
        )
        val secondProvider = CountingSuccessProvider(newToken)
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(firstProvider)
        dispatcher.scheduler.advanceUntilIdle()

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()

        // Capture generation before registering new provider
        val gen = manager.invalidate()

        // New provider registration clears connectivityWaitJob
        manager.registerProvider(secondProvider)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull("registerProvider cleared connectivity job", manager.connectivityWaitJob)

        // A late clearTokenState with the old generation is a no-op — must not wipe new state
        manager.clearTokenState(expectedGeneration = gen)
        dispatcher.scheduler.advanceUntilIdle()

        // New session is still healthy
        val result = manager.currentToken()
        assertEquals(newToken, result.rawToken)
    }

    // MARK: - Fake NetworkMonitor

    /**
     * A controllable [NetworkMonitor] implementation that lets tests drive connectivity transitions.
     */
    private class FakeNetworkMonitor : NetworkMonitor {
        private val observers = CopyOnWriteArrayList<NetworkObserver>()

        fun simulateConnected(isConnected: Boolean) {
            observers.forEach { it(isConnected) }
        }

        fun observerCount(): Int = observers.size

        override fun onNetworkChange(observer: NetworkObserver) {
            observers += observer
        }

        override fun offNetworkChange(observer: NetworkObserver) {
            observers -= observer
        }

        override fun isNetworkConnected(): Boolean = false

        override fun getNetworkType(): NetworkMonitor.NetworkType = NetworkMonitor.NetworkType.Offline
    }

    // MARK: - Helpers (mirrored from KlaviyoAuthTokenManagerRefreshTest)

    private class ScriptedProvider(
        private val results: ArrayDeque<Result<String>>
    ) : AuthTokenProvider {
        var callCount = 0
            private set

        override fun fetchToken(callback: AuthTokenProvider.Callback) {
            callCount++
            val result = results.removeFirstOrNull()
                ?: throw AssertionError(
                    "ScriptedProvider: unexpected call #$callCount — no more scripted results"
                )
            result.fold(
                onSuccess = callback::onSuccess,
                onFailure = callback::onFailure
            )
        }
    }

    private class CountingSuccessProvider(private val jwt: String) : AuthTokenProvider {
        var callCount = 0
            private set

        override fun fetchToken(callback: AuthTokenProvider.Callback) {
            callCount++
            callback.onSuccess(jwt)
        }
    }
}

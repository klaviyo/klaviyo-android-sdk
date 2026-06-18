package com.klaviyo.core.auth

import com.klaviyo.core.Registry
import com.klaviyo.core.networking.NetworkMonitor
import com.klaviyo.core.networking.NetworkObserver
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
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
 * Tests for the connectivity-driven refresh retry path in [KlaviyoAuthTokenManager].
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

    @Before
    override fun setup() {
        super.setup()
        fakeNetworkMonitor = FakeNetworkMonitor()
        every { Registry.networkMonitor } returns fakeNetworkMonitor
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

    /** Fires the first pending clock task and advances the test dispatcher until idle. */
    private fun executeScheduledRefresh() {
        val task = staticClock.scheduledTasks.first()
        staticClock.execute(task.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()
    }

    /**
     * Shared arrange/act/assert for the four exception-variant retry tests. Sets up a scripted
     * provider that fails once with [exception] then succeeds, fires the refresh, simulates
     * connectivity restored, and asserts the retry ran.
     */
    private fun assertConnectivityRetryFires(exception: Exception) = runTest(dispatcher) {
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(makeJwt()),
                    Result.failure(exception),
                    Result.success(makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600))
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, provider.callCount)

        executeScheduledRefresh()
        assertEquals(2, provider.callCount)

        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("retry after ${exception::class.simpleName}", 3, provider.callCount)
    }

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
        executeScheduledRefresh()
        assertEquals("refresh attempt failed", 2, provider.callCount)

        // connectivityWaitJob should be armed
        assertNotNull(
            "connectivityWaitJob should be armed after network failure",
            manager.connectivityWaitJob
        )
        verify { spyLog.info(any()) }

        // Simulate connectivity restored
        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "connectivity retry should invoke provider a third time",
            3,
            provider.callCount
        )
        verify { spyLog.info(any()) }
    }

    @Test
    fun `connectivity retry fires after UnknownHostException`() {
        assertConnectivityRetryFires(UnknownHostException("host unknown"))
    }

    @Test
    fun `connectivity retry fires after SocketTimeoutException`() {
        assertConnectivityRetryFires(SocketTimeoutException("timed out"))
    }

    @Test
    fun `connectivity retry fires after ConnectException`() {
        assertConnectivityRetryFires(ConnectException("connection refused"))
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

        executeScheduledRefresh()
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

    @Test
    fun `connectivity retry fires immediately when device is already online`() = runTest(
        dispatcher
    ) {
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
        assertEquals(1, provider.callCount)

        // Mark network as already online before firing the refresh
        fakeNetworkMonitor.connected = true

        // executeScheduledRefresh() calls advanceUntilIdle() internally, which runs:
        //  1. performScheduledRefresh → fails with SocketTimeoutException → arms connectivity job
        //  2. the armed coroutine → isNetworkConnected() is true → resumes immediately → retries
        // Both happen in the same advanceUntilIdle pass, so count is 3 on return.
        executeScheduledRefresh()
        assertEquals(
            "retry should fire immediately without waiting for a future connectivity event",
            3,
            provider.callCount
        )
    }

    @Test
    fun `persistent provider failure on connected device arms a waiting job not a tight loop`() =
        runTest(dispatcher) {
            // Scenario: device is online the whole time, but the provider keeps failing with
            // IOException (e.g. the JWT endpoint itself is down). The first arm should resume
            // immediately (resumeImmediatelyIfConnected=true). The second arm, kicked off by
            // performScheduledRefresh(allowImmediateConnectivityRetry=false), must NOT resume
            // immediately again — it waits for an actual connectivity transition. This prevents
            // an uncontrolled tight-loop.
            val successToken = makeJwt(EXP_SECONDS + 100, IAT_SECONDS + 100)
            val provider = ScriptedProvider(
                ArrayDeque(
                    listOf(
                        Result.success(makeJwt()), // eager fetch succeeds
                        Result.failure(IOException("endpoint down")), // timer refresh fails
                        Result.failure(IOException("still down")), // immediate retry fails
                        Result.success(successToken) // eventual success
                    )
                )
            )
            val manager = KlaviyoAuthTokenManager()
            manager.registerProvider(provider)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, provider.callCount)

            // Device is already connected for the entire test
            fakeNetworkMonitor.connected = true

            // executeScheduledRefresh() + advanceUntilIdle() runs:
            //  1. timer fires → performScheduledRefresh(immediate=true) → fails (count=2)
            //  2. arm1(resumeImmediately=true) → isNetworkConnected()=true → immediate resume
            //  3. performScheduledRefresh(immediate=false) → fails (count=3)
            //  4. arm2(resumeImmediately=false) → skips immediate check → suspends
            // After advanceUntilIdle the coroutine tree is idle with arm2 waiting.
            executeScheduledRefresh()
            assertEquals("two failures, no extra calls", 3, provider.callCount)
            assertNotNull("arm2 should be waiting (not looping)", manager.connectivityWaitJob)
            assertEquals(
                "arm2 should be active — it is waiting, not looping",
                false,
                manager.connectivityWaitJob?.isCancelled ?: true
            )

            // Simulate a genuine connectivity transition — arm2 resumes, retry succeeds
            fakeNetworkMonitor.simulateConnected(isConnected = true)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("eventual success on real connectivity event", 4, provider.callCount)
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
        executeScheduledRefresh()
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

        assertEquals("second job should be active", false, secondJob?.isCancelled ?: true)

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

        executeScheduledRefresh()
        val armedJob = manager.connectivityWaitJob
        assertNotNull("connectivityWaitJob should be armed", armedJob)

        // Register new provider — should cancel the pending connectivity wait
        manager.registerProvider(secondProvider)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(
            "connectivityWaitJob should be cleared after registerProvider",
            manager.connectivityWaitJob
        )
        assertEquals("cancelled job should be inactive", true, armedJob?.isCancelled ?: false)
        assertEquals(
            "no network observer should remain after provider swap",
            0,
            fakeNetworkMonitor.observerCount()
        )
    }

    // MARK: - Stale-guard: profileResetPending prevents arming

    @Test
    fun `network failure during profile reset does not arm connectivity wait job`() = runTest(
        dispatcher
    ) {
        // invalidate() sets profileResetPending = true but does NOT cancel the refresh job, so
        // the scheduled refresh fires normally — the new guard is the profileResetPending check
        // in the catch block, not the generation guard in markRefreshTimerFired.
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(makeJwt()),
                    Result.failure(IOException("network down"))
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        // Set profileResetPending = true before the scheduled refresh fires
        manager.invalidate()

        executeScheduledRefresh()

        assertNull(
            "armConnectivityWaitJob must not fire when profileResetPending is true",
            manager.connectivityWaitJob
        )
        assertEquals(0, fakeNetworkMonitor.observerCount())
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

        executeScheduledRefresh()

        assertNull("RuntimeException must not arm connectivityWaitJob", manager.connectivityWaitJob)
        assertEquals(
            "no observer registered for non-network failure",
            0,
            fakeNetworkMonitor.observerCount()
        )
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

        executeScheduledRefresh()

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

        executeScheduledRefresh()

        val armedJob = manager.connectivityWaitJob
        assertNotNull("job must be armed before clear", armedJob)

        // Clear token state (simulates logout / resetProfile)
        manager.clearTokenState()

        assertNull(
            "connectivityWaitJob must be null after clearTokenState",
            manager.connectivityWaitJob
        )
        assertEquals("armed job must be cancelled", true, armedJob?.isCancelled ?: false)
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

        executeScheduledRefresh()

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
     * Set [connected] to control the return value of [isNetworkConnected].
     */
    private class FakeNetworkMonitor : NetworkMonitor {
        private val observers = CopyOnWriteArrayList<NetworkObserver>()
        var connected: Boolean = false

        fun simulateConnected(isConnected: Boolean) {
            connected = isConnected
            observers.forEach { it(isConnected) }
        }

        fun observerCount(): Int = observers.size

        override fun onNetworkChange(observer: NetworkObserver) {
            observers += observer
        }

        override fun offNetworkChange(observer: NetworkObserver) {
            observers -= observer
        }

        override fun isNetworkConnected(): Boolean = connected

        override fun getNetworkType(): NetworkMonitor.NetworkType = NetworkMonitor.NetworkType.Offline
    }

    // MARK: - Test doubles

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

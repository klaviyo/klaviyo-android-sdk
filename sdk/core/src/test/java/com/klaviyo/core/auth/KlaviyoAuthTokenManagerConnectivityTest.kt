package com.klaviyo.core.auth

import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.core.networking.NetworkMonitor
import com.klaviyo.core.networking.NetworkObserver
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
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
    private val lifecycleObserver = slot<ActivityObserver>()

    @Before
    override fun setup() {
        super.setup()
        fakeNetworkMonitor = FakeNetworkMonitor()
        every { Registry.networkMonitor } returns fakeNetworkMonitor
        every { mockLifecycleMonitor.onActivityEvent(capture(lifecycleObserver)) } returns Unit
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
        val task = staticClock.scheduledTasks.firstOrNull()
            ?: throw AssertionError("Expected at least one scheduled refresh task")
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

    /**
     * Fails the eager fetch and then the scheduled refresh with [exception], asserting neither
     * failure arms connectivity recovery.
     */
    private fun assertConnectivityWaitNotArmed(exception: Exception) = runTest(dispatcher) {
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.failure(exception),
                    Result.success(makeJwt()),
                    Result.failure(exception)
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull("eager ${exception::class.simpleName}", manager.connectivityWaitJob())

        manager.currentToken()
        executeScheduledRefresh()
        assertEquals(3, provider.callCount)
        assertNull("refresh ${exception::class.simpleName}", manager.connectivityWaitJob())
        assertEquals(0, fakeNetworkMonitor.observerCount())
    }

    // MARK: - Retry fires after reconnect

    @Test
    fun `initial eager network failure retries after reconnect`() = runTest(dispatcher) {
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.failure(UnknownHostException("offline")),
                    Result.success(makeJwt())
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, provider.callCount)
        assertNotNull(manager.connectivityWaitJob())

        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, provider.callCount)
        assertEquals(makeJwt(), manager.currentToken().rawToken)
    }

    @Test
    fun `interactive network failure retries after reconnect`() = runTest(dispatcher) {
        val retryToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(makeJwt()),
                    Result.failure(ConnectException("offline")),
                    Result.success(retryToken)
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        manager.clearTokenState()

        runCatching { manager.currentToken() }
        assertEquals(2, provider.callCount)
        assertNotNull(manager.connectivityWaitJob())

        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, provider.callCount)
        assertEquals(retryToken, manager.currentToken().rawToken)
    }

    @Test
    fun `new demand supersedes recovery from an older failure`() = runTest(dispatcher) {
        val demandToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.failure(UnknownHostException("offline")),
                    Result.success(demandToken)
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(manager.connectivityWaitJob())

        assertEquals(demandToken, manager.currentToken().rawToken)
        assertNull(manager.connectivityWaitJob())

        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, provider.callCount)
    }

    @Test
    fun `initial failure on a connected device retries once then waits`() = runTest(
        dispatcher
    ) {
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.failure(UnknownHostException("endpoint down")),
                    Result.failure(UnknownHostException("still down")),
                    Result.success(makeJwt())
                )
            )
        )
        fakeNetworkMonitor.connected = true
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, provider.callCount)
        assertEquals(false, manager.connectivityWaitJob()?.isCancelled ?: true)

        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, provider.callCount)
    }

    @Test
    fun `connectivity retry fires after network comes back online`() = runTest(
        dispatcher
    ) {
        val initialToken = makeJwt()
        val retryToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(initialToken),
                    Result.failure(UnknownHostException("network down")),
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
            manager.connectivityWaitJob()
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
    fun `foreground refresh cancels pending connectivity retry after network failure`() = runTest(
        dispatcher
    ) {
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(makeJwt()),
                    Result.failure(UnknownHostException("network down")),
                    Result.success(makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600))
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        executeScheduledRefresh()
        assertNotNull(manager.connectivityWaitJob())
        assertEquals(1, fakeNetworkMonitor.observerCount())

        lifecycleObserver.captured.invoke(ActivityEvent.FirstStarted(mockActivity))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, provider.callCount)
        assertNull(manager.connectivityWaitJob())
        assertEquals(0, fakeNetworkMonitor.observerCount())

        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, provider.callCount)
    }

    @Test
    fun `foreground expiration preserves recovery for an in-flight scheduled refresh`() = runTest(
        dispatcher
    ) {
        val provider = PendingScriptedProvider(
            listOf(
                Result.success(makeJwt()),
                null,
                Result.success(makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600))
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        val timer = staticClock.scheduledTasks.first()
        staticClock.execute(timer.time - staticClock.time)
        dispatcher.scheduler.runCurrent()
        assertEquals(2, provider.callCount)

        staticClock.time = EXP_SECONDS * 1000L
        lifecycleObserver.captured.invoke(ActivityEvent.FirstStarted(mockActivity))
        dispatcher.scheduler.runCurrent()
        provider.failPending(UnknownHostException("network down"))
        dispatcher.scheduler.runCurrent()

        assertNotNull(manager.connectivityWaitJob())
        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, provider.callCount)
    }

    @Test
    fun `foreground expiration preserves recovery for an in-flight connectivity refresh`() =
        runTest(dispatcher) {
            val provider = PendingScriptedProvider(
                listOf(
                    Result.success(makeJwt()),
                    Result.failure(UnknownHostException("scheduled refresh offline")),
                    null,
                    Result.success(makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600))
                )
            )
            val manager = KlaviyoAuthTokenManager()
            manager.registerProvider(provider)
            dispatcher.scheduler.advanceUntilIdle()

            executeScheduledRefresh()
            assertEquals(2, provider.callCount)
            assertNotNull(manager.connectivityWaitJob())

            fakeNetworkMonitor.simulateConnected(isConnected = true)
            dispatcher.scheduler.runCurrent()
            assertEquals(3, provider.callCount)

            staticClock.time = EXP_SECONDS * 1000L
            lifecycleObserver.captured.invoke(ActivityEvent.FirstStarted(mockActivity))
            dispatcher.scheduler.runCurrent()
            provider.failPending(UnknownHostException("connectivity refresh offline"))
            dispatcher.scheduler.runCurrent()

            assertNotNull(manager.connectivityWaitJob())
            fakeNetworkMonitor.simulateConnected(isConnected = true)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(4, provider.callCount)
        }

    @Test
    fun `reconnect during a foreground missed refresh does not launch a duplicate refresh`() =
        runTest(dispatcher) {
            // Scheduled refresh fails offline and arms a connectivity wait. Foregrounding past the
            // missed target launches a new refresh, then the network returns while it is in
            // flight. When that shared fetch fails, the immediate retry must still fire.
            val provider = PendingScriptedProvider(
                listOf(
                    Result.success(makeJwt()),
                    Result.failure(UnknownHostException("scheduled refresh offline")),
                    null,
                    Result.success(makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600))
                )
            )
            val manager = KlaviyoAuthTokenManager()
            manager.registerProvider(provider)
            dispatcher.scheduler.advanceUntilIdle()

            executeScheduledRefresh()
            assertEquals(2, provider.callCount)
            assertNotNull(manager.connectivityWaitJob())

            lifecycleObserver.captured.invoke(ActivityEvent.FirstStarted(mockActivity))
            dispatcher.scheduler.runCurrent()
            assertEquals("foreground launched the missed refresh", 3, provider.callCount)

            fakeNetworkMonitor.simulateConnected(isConnected = true)
            dispatcher.scheduler.runCurrent()
            assertEquals("reconnect shares the in-flight fetch", 3, provider.callCount)

            provider.failPending(UnknownHostException("foreground refresh offline"))
            dispatcher.scheduler.runCurrent()

            assertEquals(
                "immediate retry fires without another network event",
                4,
                provider.callCount
            )
            assertNull(manager.connectivityWaitJob())
            assertEquals(0, fakeNetworkMonitor.observerCount())
        }

    @Test
    fun `foreground expiration fetches after a connectivity refresh fails with a non-connectivity error`() =
        runTest(dispatcher) {
            val provider = ScriptedProvider(
                ArrayDeque(
                    listOf(
                        Result.success(makeJwt()),
                        Result.failure(UnknownHostException("scheduled refresh offline")),
                        Result.failure(RuntimeException("http 500")),
                        Result.success(makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600))
                    )
                )
            )
            val manager = KlaviyoAuthTokenManager()
            manager.registerProvider(provider)
            dispatcher.scheduler.advanceUntilIdle()

            executeScheduledRefresh()
            assertEquals(2, provider.callCount)

            fakeNetworkMonitor.simulateConnected(isConnected = true)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(3, provider.callCount)
            assertNull(manager.connectivityWaitJob())

            staticClock.time = EXP_SECONDS * 1000L
            lifecycleObserver.captured.invoke(ActivityEvent.FirstStarted(mockActivity))
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(4, provider.callCount)
        }

    @Test
    fun `foreground expiration fetches after a connectivity refresh reports provider cancellation`() =
        runTest(dispatcher) {
            val provider = ScriptedProvider(
                ArrayDeque(
                    listOf(
                        Result.success(makeJwt()),
                        Result.failure(UnknownHostException("scheduled refresh offline")),
                        Result.failure(CancellationException("host scope cancelled")),
                        Result.success(makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600))
                    )
                )
            )
            val manager = KlaviyoAuthTokenManager()
            manager.registerProvider(provider)
            dispatcher.scheduler.advanceUntilIdle()

            executeScheduledRefresh()
            assertEquals(2, provider.callCount)

            fakeNetworkMonitor.simulateConnected(isConnected = true)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(3, provider.callCount)
            assertNull(manager.connectivityWaitJob())

            staticClock.time = EXP_SECONDS * 1000L
            lifecycleObserver.captured.invoke(ActivityEvent.FirstStarted(mockActivity))
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(4, provider.callCount)
        }

    @Test
    fun `failed foreground fetch retains an armed connectivity retry`() = runTest(dispatcher) {
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(makeJwt()),
                    Result.failure(UnknownHostException("scheduled refresh offline")),
                    Result.failure(UnknownHostException("foreground fetch offline")),
                    Result.success(makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600))
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        executeScheduledRefresh()
        assertEquals(2, provider.callCount)
        assertNotNull(manager.connectivityWaitJob())

        staticClock.time = EXP_SECONDS * 1000L
        lifecycleObserver.captured.invoke(ActivityEvent.FirstStarted(mockActivity))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, provider.callCount)
        assertNotNull(manager.connectivityWaitJob())
        assertEquals(1, fakeNetworkMonitor.observerCount())

        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(4, provider.callCount)
    }

    @Test
    fun `queued connectivity retry cannot fetch after clearTokenState returns`() = runTest(
        dispatcher
    ) {
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(makeJwt()),
                    Result.failure(UnknownHostException("network down")),
                    Result.success(makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600))
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        executeScheduledRefresh()
        assertEquals(2, provider.callCount)

        fakeNetworkMonitor.simulateConnected(isConnected = true)
        manager.clearTokenState()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, provider.callCount)
    }

    @Test
    fun `connectivity retry fires after UnknownHostException`() {
        assertConnectivityRetryFires(UnknownHostException("host unknown"))
    }

    @Test
    fun `connectivity retry fires after NoRouteToHostException`() {
        assertConnectivityRetryFires(NoRouteToHostException("no route"))
    }

    @Test
    fun `connectivity retry fires after a wrapped connectivity cause`() {
        assertConnectivityRetryFires(IOException("request failed", ConnectException("refused")))
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
                    Result.failure(UnknownHostException("network down")),
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
                    Result.failure(UnknownHostException("host unknown")),
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
        //  1. performScheduledRefresh → fails with UnknownHostException → arms connectivity job
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
            // UnknownHostException (e.g. the JWT endpoint itself is down). The first arm should resume
            // immediately (resumeImmediatelyIfConnected=true). The second arm, kicked off by
            // performScheduledRefresh(allowImmediateConnectivityRetry=false), must NOT resume
            // immediately again — it waits for an actual connectivity transition. This prevents
            // an uncontrolled tight-loop.
            val successToken = makeJwt(EXP_SECONDS + 100, IAT_SECONDS + 100)
            val provider = ScriptedProvider(
                ArrayDeque(
                    listOf(
                        Result.success(makeJwt()), // eager fetch succeeds
                        Result.failure(UnknownHostException("endpoint down")), // timer refresh fails
                        Result.failure(UnknownHostException("still down")), // immediate retry fails
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
            assertNotNull("arm2 should be waiting (not looping)", manager.connectivityWaitJob())
            assertEquals(
                "arm2 should be active — it is waiting, not looping",
                false,
                manager.connectivityWaitJob()?.isCancelled ?: true
            )

            // Simulate a genuine connectivity transition — arm2 resumes, retry succeeds
            fakeNetworkMonitor.simulateConnected(isConnected = true)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("eventual success on real connectivity event", 4, provider.callCount)
        }

    @Test
    fun `foreground failure on a connected device keeps the armed wait`() = runTest(dispatcher) {
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(makeJwt()),
                    Result.failure(UnknownHostException("endpoint down")),
                    Result.failure(UnknownHostException("still down")),
                    Result.failure(UnknownHostException("foreground fetch down")),
                    Result.success(makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600))
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        fakeNetworkMonitor.connected = true

        executeScheduledRefresh()
        assertEquals(3, provider.callCount)
        val armedJob = manager.connectivityWaitJob()
        assertNotNull(armedJob)

        staticClock.time = EXP_SECONDS * 1000L
        lifecycleObserver.captured.invoke(ActivityEvent.FirstStarted(mockActivity))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(4, provider.callCount)
        assertEquals(armedJob, manager.connectivityWaitJob())

        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(5, provider.callCount)
    }

    @Test
    fun `connectivity retry that outlives its refresh waiter does not retry immediately on failure`() =
        runTest(dispatcher) {
            val provider = PendingScriptedProvider(
                listOf(
                    Result.success(makeJwt()),
                    Result.failure(UnknownHostException("scheduled refresh offline")),
                    null,
                    Result.success(makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600))
                )
            )
            val manager = KlaviyoAuthTokenManager()
            manager.registerProvider(provider)
            dispatcher.scheduler.advanceUntilIdle()

            executeScheduledRefresh()
            assertEquals(2, provider.callCount)
            assertNotNull(manager.connectivityWaitJob())

            fakeNetworkMonitor.simulateConnected(isConnected = true)
            dispatcher.scheduler.runCurrent()
            assertEquals(3, provider.callCount)

            dispatcher.scheduler.advanceTimeBy(AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS + 1)
            dispatcher.scheduler.runCurrent()
            assertNull(manager.connectivityWaitJob())

            provider.failPending(ConnectException("connectivity retry offline"))
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(3, provider.callCount)
            assertNull(manager.connectivityWaitJob())
            assertEquals(0, fakeNetworkMonitor.observerCount())
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
                    Result.failure(UnknownHostException("flap 1")),
                    Result.failure(UnknownHostException("flap 2")),
                    Result.failure(UnknownHostException("flap 3")),
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

        val firstJob = manager.connectivityWaitJob()
        assertNotNull("first job should be armed", firstJob)

        // Simulate connectivity restored → retry fires → fails again → re-arms
        fakeNetworkMonitor.simulateConnected(isConnected = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("first retry fired", 3, provider.callCount)

        // Re-armed after second failure
        val secondJob = manager.connectivityWaitJob()
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
                    Result.failure(UnknownHostException("network down"))
                )
            )
        )
        val secondProvider = CountingSuccessProvider(newToken)
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(firstProvider)
        dispatcher.scheduler.advanceUntilIdle()

        executeScheduledRefresh()
        val armedJob = manager.connectivityWaitJob()
        assertNotNull("connectivityWaitJob should be armed", armedJob)

        // Register new provider — should cancel the pending connectivity wait
        manager.registerProvider(secondProvider)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(
            "connectivityWaitJob should be cleared after registerProvider",
            manager.connectivityWaitJob()
        )
        assertEquals("cancelled job should be inactive", true, armedJob?.isCancelled ?: false)
        assertEquals(
            "no network observer should remain after provider swap",
            0,
            fakeNetworkMonitor.observerCount()
        )
    }

    // MARK: - Stale-guard: profile reset prevents arming

    @Test
    fun `network failure during profile reset does not arm connectivity wait job`() = runTest(
        dispatcher
    ) {
        // A refresh timer is scheduled, then invalidate() queues its Invalidate command before the
        // timer fires and queues TimerFired. Invalidate runs first and retires the refresh id, so
        // the stale TimerFired is rejected and the scripted network failure is never reached.
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(makeJwt()),
                    Result.failure(UnknownHostException("network down"))
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("eager fetch ran", 1, provider.callCount)
        assertEquals("refresh timer scheduled", 1, staticClock.scheduledTasks.size)

        manager.invalidate()

        executeScheduledRefresh()
        assertEquals("refresh timer fired", 0, staticClock.scheduledTasks.size)
        assertEquals("stale timer must not launch a refresh", 1, provider.callCount)

        assertNull(
            "connectivity job must not arm after the profile reset",
            manager.connectivityWaitJob()
        )
        assertEquals(0, fakeNetworkMonitor.observerCount())
    }

    @Test
    fun `network failure after mid-fetch profile transition does not arm connectivity wait job`() =
        runTest(dispatcher) {
            // The timer refresh is mid-fetch when invalidate() runs inside fetchToken, before the
            // provider reports its network failure. Invalidate is handled before the fetch result,
            // so it fails the refresh waiter as superseded and drops the stale fetch result. The
            // superseded refresh never posts RefreshFailed, so no connectivity wait is armed.
            val manager = KlaviyoAuthTokenManager()
            val provider = object : AuthTokenProvider {
                var callCount = 0
                var invalidatedGeneration: Long? = null

                override fun fetchToken(callback: AuthTokenProvider.Callback) {
                    callCount++
                    if (callCount == 1) {
                        callback.onSuccess(makeJwt())
                    } else {
                        invalidatedGeneration = manager.invalidate()
                        callback.onFailure(UnknownHostException("network down"))
                    }
                }
            }

            manager.registerProvider(provider)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("eager fetch ran", 1, provider.callCount)

            executeScheduledRefresh()
            assertEquals("timer refresh invoked the provider", 2, provider.callCount)
            assertNotNull("profile reset ran mid-fetch", provider.invalidatedGeneration)

            assertNull(
                "connectivity job must not arm for a superseded refresh",
                manager.connectivityWaitJob()
            )
            assertEquals(0, fakeNetworkMonitor.observerCount())
        }

    // MARK: - Non-network failures do not arm the retry

    @Test
    fun `generic IOException does not arm connectivity wait job`() {
        assertConnectivityWaitNotArmed(IOException("not a connectivity failure"))
    }

    @Test
    fun `SocketTimeoutException does not arm connectivity wait job`() {
        assertConnectivityWaitNotArmed(SocketTimeoutException("timed out"))
    }

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

        assertNull(
            "RuntimeException must not arm connectivityWaitJob",
            manager.connectivityWaitJob()
        )
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

        assertNull(
            "ValidationFailed must not arm connectivityWaitJob",
            manager.connectivityWaitJob()
        )
        assertEquals(0, fakeNetworkMonitor.observerCount())
    }

    // MARK: - unregisterProvider cancels the connectivity wait job

    @Test
    fun `unregisterProvider cancels and clears connectivity wait job`() = runTest(dispatcher) {
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(makeJwt()),
                    Result.failure(UnknownHostException("network down"))
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        executeScheduledRefresh()

        val armedJob = manager.connectivityWaitJob()
        assertNotNull("connectivityWaitJob should be armed after network failure", armedJob)

        manager.unregisterProvider()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(
            "connectivityWaitJob should be null after unregisterProvider",
            manager.connectivityWaitJob()
        )
        assertEquals("armed job must be cancelled", true, armedJob?.isCancelled ?: false)
        assertEquals(
            "network observer should be de-registered on cancellation",
            0,
            fakeNetworkMonitor.observerCount()
        )
    }

    // MARK: - clearTokenState cancels the connectivity wait job

    @Test
    fun `clearTokenState cancels and clears connectivity wait job`() = runTest(dispatcher) {
        val initialToken = makeJwt()
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(initialToken),
                    Result.failure(UnknownHostException("network down"))
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()
        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        executeScheduledRefresh()

        val armedJob = manager.connectivityWaitJob()
        assertNotNull("job must be armed before clear", armedJob)

        // Clear token state (simulates logout / resetProfile)
        manager.clearTokenState()

        assertNull(
            "connectivityWaitJob must be null after clearTokenState",
            manager.connectivityWaitJob()
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
                    Result.failure(UnknownHostException("network down"))
                )
            )
        )
        val secondProvider = CountingSuccessProvider(newToken)
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(firstProvider)
        dispatcher.scheduler.advanceUntilIdle()

        executeScheduledRefresh()

        val generation = manager.invalidate()

        manager.registerProvider(secondProvider)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull("registerProvider cleared connectivity job", manager.connectivityWaitJob())

        manager.clearTokenState(generation)
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

    /** Replays [steps] in order; a null step holds the callback until [failPending]. */
    private class PendingScriptedProvider(
        private val steps: List<Result<String>?>
    ) : AuthTokenProvider {
        var callCount = 0
            private set
        private var pending: AuthTokenProvider.Callback? = null

        override fun fetchToken(callback: AuthTokenProvider.Callback) {
            callCount++
            val step = steps.getOrElse(callCount - 1) {
                throw AssertionError(
                    "PendingScriptedProvider: unexpected call #$callCount — no more scripted results"
                )
            }
            if (step == null) {
                pending = callback
            } else {
                step.fold(onSuccess = callback::onSuccess, onFailure = callback::onFailure)
            }
        }

        fun failPending(error: Throwable) {
            requireNotNull(pending).onFailure(error)
            pending = null
        }
    }
}

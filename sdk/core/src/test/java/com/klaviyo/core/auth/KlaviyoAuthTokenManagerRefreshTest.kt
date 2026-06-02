package com.klaviyo.core.auth

import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.slot
import java.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KlaviyoAuthTokenManagerRefreshTest : BaseTest() {

    companion object {
        private const val NOW_SECONDS = TIME / 1000L // staticClock baseline = 1234567890s
        private const val IAT_SECONDS = NOW_SECONDS - 60
        private const val EXP_SECONDS = NOW_SECONDS + 3600
    }

    private val observerSlot = slot<ActivityObserver>()

    @Before
    override fun setup() {
        super.setup()
        every { mockLifecycleMonitor.onActivityEvent(capture(observerSlot)) } returns Unit
    }

    // Fires a FirstStarted event into the captured observer and drains the dispatcher.
    private fun KlaviyoAuthTokenManager.fireFirstStarted() {
        observerSlot.captured.invoke(ActivityEvent.FirstStarted(mockActivity))
        dispatcher.scheduler.advanceUntilIdle()
    }

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

    // MARK: - computeRefreshTarget (pure-function tests, no coroutines)

    @Test
    fun `computeRefreshTarget returns 90-percent of lifetime when inside clamps`() {
        // iat=1000s, exp=4600s → lifetime=3600s, ideal=iat+3240=4240s
        // upper=4570s, lower=1005s → ideal is inside bounds
        val token = ValidatedToken("", expiresAtEpochSeconds = 4600L, issuedAtEpochSeconds = 1000L)
        val targetMs = KlaviyoAuthTokenManager.computeRefreshTarget(token, nowMs = 1_000_000L)
        assertEquals(4_240_000L, targetMs)
    }

    @Test
    fun `computeRefreshTarget clamps to upperBound when ideal exceeds exp minus leeway`() {
        // iat=0s, exp=100s, now=0s → ideal=90s, upper=70s, lower=5s → upper wins
        val token = ValidatedToken("", expiresAtEpochSeconds = 100L, issuedAtEpochSeconds = 0L)
        val targetMs = KlaviyoAuthTokenManager.computeRefreshTarget(token, nowMs = 0L)
        assertEquals(70_000L, targetMs)
    }

    @Test
    fun `computeRefreshTarget clamps to lowerBound when ideal is in the past`() {
        // iat=0s, exp=10s, now=1000s → ideal=9s, upper=-20s, lower=1005s → lower wins
        val token = ValidatedToken("", expiresAtEpochSeconds = 10L, issuedAtEpochSeconds = 0L)
        val targetMs = KlaviyoAuthTokenManager.computeRefreshTarget(token, nowMs = 1_000_000L)
        assertEquals(1_005_000L, targetMs)
    }

    // MARK: - Scheduling integration

    @Test
    fun `scheduleRefresh fires at computed target and chains next refresh`() = runTest(dispatcher) {
        val token = makeJwt()
        val provider = CountingSuccessProvider(token)
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, provider.callCount)
        assertEquals(
            "refresh should be scheduled after eager fetch",
            1,
            staticClock.scheduledTasks.size
        )

        // Advance clock to fire the scheduled timer task
        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("timer should have triggered a second provider call", 2, provider.callCount)
        assertEquals(
            "chained refresh should be scheduled after success",
            1,
            staticClock.scheduledTasks.size
        )
    }

    @Test
    fun `registerProvider cancels pending refresh job`() = runTest(dispatcher) {
        val tokenA = makeJwt()
        val tokenB = makeJwt(EXP_SECONDS + 1, IAT_SECONDS + 1)
        val providerA = CountingSuccessProvider(tokenA)
        val providerB = CountingSuccessProvider(tokenB)
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(providerA)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, staticClock.scheduledTasks.size) // A's refresh scheduled

        // Swap to B before the timer fires — should cancel A's scheduled refresh
        manager.registerProvider(providerB)
        dispatcher.scheduler.advanceUntilIdle()

        // Advance well past where A's refresh would have fired
        staticClock.execute(5_000_000L)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("A's refresh should have been cancelled, not fired", 1, providerA.callCount)
    }

    // MARK: - Foreground transitions

    @Test
    fun `foreground with still-valid token is no-op`() = runTest(dispatcher) {
        val provider = CountingSuccessProvider(makeJwt())
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        manager.fireFirstStarted()

        assertEquals("still-valid foreground should not trigger re-fetch", 1, provider.callCount)
    }

    @Test
    fun `non-FirstStarted lifecycle events do not trigger foreground handler`() = runTest(
        dispatcher
    ) {
        val provider = CountingSuccessProvider(makeJwt())
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        val observer = observerSlot.captured
        observer.invoke(ActivityEvent.Started(mockActivity))
        observer.invoke(ActivityEvent.Stopped(mockActivity))
        observer.invoke(ActivityEvent.AllStopped())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("non-FirstStarted events must be ignored", 1, provider.callCount)
    }

    @Test
    fun `foreground with missed refresh fires exactly once`() = runTest(dispatcher) {
        val provider = CountingSuccessProvider(makeJwt())
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, staticClock.scheduledTasks.size)

        // Simulate background time passing past the refresh target without the timer firing
        // (e.g., Doze suppressed the JVM Timer thread). Advance time directly, not via execute().
        val timerTask = staticClock.scheduledTasks.first()
        staticClock.time = timerTask.time + 1_000L

        // Foreground transition should detect the missed refresh and fire it immediately
        manager.fireFirstStarted()

        assertEquals(
            "missed refresh should trigger one additional provider call",
            2,
            provider.callCount
        )
        assertEquals(
            "chained refresh should be scheduled after foreground retry",
            1,
            staticClock.scheduledTasks.size
        )
    }

    @Test
    fun `foreground with expired token clears cache and eager-fetches`() = runTest(dispatcher) {
        val provider = CountingSuccessProvider(makeJwt())
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, provider.callCount)

        // Advance clock past the token's validity window (exp - leeway) without firing tasks
        staticClock.time = (EXP_SECONDS - JWTParser.DEFAULT_LEEWAY_SECONDS) * 1000L

        // Foreground transition should detect expiry and kick off an eager re-fetch
        manager.fireFirstStarted()

        assertEquals("expired-token foreground should trigger a re-fetch", 2, provider.callCount)
    }

    // MARK: - Helpers

    private class CountingSuccessProvider(private val jwt: String) : AuthTokenProvider {
        var callCount = 0
            private set

        override fun fetchToken(callback: AuthTokenProvider.Callback) {
            callCount++
            callback.onSuccess(jwt)
        }
    }
}

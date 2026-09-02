package com.klaviyo.core.auth

import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `computeRefreshTarget clamps to lowerBound when token lifetime is shorter than leeway`() {
        // Degenerate: iat=0s, exp=4s, now=0s → ideal=3.6s, upper=-26s (exp-leeway < 0)
        // Both ideal and upper fall below lowerBound (5s) → lowerBound wins
        val token = ValidatedToken("", expiresAtEpochSeconds = 4L, issuedAtEpochSeconds = 0L)
        val targetMs = KlaviyoAuthTokenManager.computeRefreshTarget(token, nowMs = 0L)
        assertEquals(5_000L, targetMs)
    }

    @Test
    fun `computeRefreshTarget degrades gracefully when exp precedes iat`() {
        // Malformed token where exp < iat — JWTParser.parseAndValidate rejects this in production,
        // but the math should produce a safe positive target rather than a negative delay.
        // iat=1000s, exp=900s, now=0 → ideal=910s, upper=870s (exp-leeway), lower=5s → upper wins
        val token = ValidatedToken("", expiresAtEpochSeconds = 900L, issuedAtEpochSeconds = 1000L)
        val targetMs = KlaviyoAuthTokenManager.computeRefreshTarget(token, nowMs = 0L)
        assertEquals(870_000L, targetMs)
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
    fun `failed scheduled refresh keeps prior cached token for callers`() = runTest(dispatcher) {
        val token = makeJwt()
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(token),
                    Result.failure(RuntimeException("refresh failed"))
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, provider.callCount)

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            "scheduled refresh should attempt one additional provider call",
            2,
            provider.callCount
        )

        val cached = manager.currentToken()
        assertEquals(token, cached.rawToken)
        assertEquals(
            "callers should keep using the prior cached token after refresh failure",
            2,
            provider.callCount
        )
    }

    @Test
    fun `timeout during scheduled refresh leaves one foreground retry`() = runTest(dispatcher) {
        // Verifies that withTimeoutOrNull returning null → AuthTokenException.TimedOut is treated
        // identically to a provider error: clearFiredFlagForFailedRefresh is called so the
        // foreground transition can detect the missed refresh and retry exactly once.
        //
        // After a real timeout the scope.async deferred is NOT cancelled — the underlying
        // invokeProvider is still suspended. The foreground retry (case 2) will JOIN that
        // still-live deferred rather than starting a new provider call, so we assert the
        // "case=missed-refresh" log rather than an incremented provider call count.
        val initialToken = makeJwt()
        val freshToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
        val provider = InitialThenResolvableProvider(initialToken)
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, provider.callCount)

        // Fire the refresh timer — provider hangs on call #2, inFlightFetch is set
        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.runCurrent()
        assertEquals("scheduled refresh should be in-flight", 2, provider.callCount)

        // Advance coroutine virtual time past BACKGROUND_FETCH_TIMEOUT_MS to trigger
        // withTimeoutOrNull inside currentTokenInternal — fires clearFiredFlagForFailedRefresh
        dispatcher.scheduler.advanceTimeBy(AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS + 1)
        dispatcher.scheduler.runCurrent()

        verify { spyLog.warning(match { it.contains("TimedOut") }, any()) }

        // Foreground retry is available: case 2 fires because refreshAtWallClockMs still points
        // to the past target and refreshTimerFired was reset by clearFiredFlagForFailedRefresh.
        // The retry joins the still-live inFlightFetch — no new provider call.
        manager.fireFirstStarted()
        verify { spyLog.info(match { it.contains("case=missed-refresh") }) }

        // Resolve the still-running in-flight deferred so runTest can complete cleanly
        provider.resolve(freshToken)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `demand caller joins scheduled refresh in-flight fetch when cache expires mid-refresh`() =
        runTest(dispatcher) {
            // Verifies allowCachedToken=false + dedup: when performScheduledRefresh (allowCachedToken=false)
            // has set inFlightFetch and the cached token subsequently expires, a demand currentToken()
            // call must join the existing deferred rather than starting a duplicate provider invocation.
            val initialToken = makeJwt()
            val freshToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
            val provider = InitialThenResolvableProvider(initialToken)
            val manager = KlaviyoAuthTokenManager()

            manager.registerProvider(provider)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, provider.callCount)

            // Fire the scheduled refresh — provider hangs on call #2, inFlightFetch is set
            val timerTask = staticClock.scheduledTasks.first()
            staticClock.execute(timerTask.time - staticClock.time)
            dispatcher.scheduler.runCurrent()
            assertEquals("scheduled refresh should be in-flight", 2, provider.callCount)

            // Advance clock past token expiry so the demand caller's optimistic read also misses
            staticClock.time = (EXP_SECONDS - JWTParser.DEFAULT_LEEWAY_SECONDS) * 1000L

            // Demand caller sees the expired cache and joins the serialized in-flight fetch.
            val demandToken = async { manager.currentToken() }
            dispatcher.scheduler.runCurrent()

            // Resolve the shared in-flight fetch — both callers receive the fresh token
            provider.resolve(freshToken)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                "demand caller should join the in-flight refresh fetch, not trigger a new provider call",
                2,
                provider.callCount
            )
            assertEquals(freshToken, demandToken.await().rawToken)
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
    fun `foreground after timer fires while refresh is in-flight is not treated as missed`() = runTest(
        dispatcher
    ) {
        val initialToken = makeJwt()
        val refreshedToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
        val provider = InitialThenResolvableProvider(initialToken)
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, provider.callCount)

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.runCurrent()
        assertEquals("scheduled refresh should be in-flight", 2, provider.callCount)

        observerSlot.captured.invoke(ActivityEvent.FirstStarted(mockActivity))
        dispatcher.scheduler.runCurrent()

        verify(inverse = true) {
            spyLog.info(match { it.contains("case=missed-refresh") })
        }

        provider.resolve(refreshedToken)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "foreground should not enqueue another forced provider fetch after timer success",
            2,
            provider.callCount
        )
    }

    @Test
    fun `foreground during in-flight refresh that subsequently fails defers retry to next foreground`() =
        runTest(dispatcher) {
            // Edge case: FirstStarted fires while the timer-driven refresh is still in-flight
            // (refreshTimerFired=true → still-valid no-op). The refresh then fails and
            // clearFiredFlagForFailedRefresh resets refreshTimerFired. refreshAtWallClockMs is NOT
            // cleared in the else branch, so the NEXT foreground transition correctly detects the
            // past target and fires case 2 (missed-refresh retry). The retry is deferred, not lost.
            //
            // A hanging provider (call #2 suspends until manually rejected) is required to keep the
            // refresh in-flight while the foreground event fires — a synchronous failure would
            // reset refreshTimerFired before the handler runs, bypassing the scenario under test.
            val initialToken = makeJwt()
            val retryToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
            var callCount = 0
            val pendingCallbacks = ArrayDeque<AuthTokenProvider.Callback>()
            val provider = object : AuthTokenProvider {
                override fun fetchToken(callback: AuthTokenProvider.Callback) {
                    callCount++
                    when (callCount) {
                        1 -> callback.onSuccess(initialToken)
                        2 -> pendingCallbacks.addLast(callback) // hangs until manually rejected
                        else -> callback.onSuccess(retryToken)
                    }
                }
            }
            val manager = KlaviyoAuthTokenManager()

            manager.registerProvider(provider)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, callCount)

            // Fire the refresh timer — provider hangs on call #2, refreshTimerFired=true
            val timerTask = staticClock.scheduledTasks.first()
            staticClock.execute(timerTask.time - staticClock.time)
            dispatcher.scheduler.runCurrent()
            assertEquals("scheduled refresh should be in-flight", 2, callCount)

            // Foreground fires while refresh is in-flight (refreshTimerFired=true) → still-valid
            observerSlot.captured.invoke(ActivityEvent.FirstStarted(mockActivity))
            dispatcher.scheduler.runCurrent()
            verify(inverse = true) {
                spyLog.info(match { it.contains("case=missed-refresh") })
            }
            verify { spyLog.info(match { it.contains("case=still-valid") }) }

            // Fail the in-flight refresh — clearFiredFlagForFailedRefresh resets refreshTimerFired=false
            pendingCallbacks.removeFirstOrNull()?.onFailure(
                RuntimeException("in-flight refresh failed")
            )
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("no new provider calls beyond the failed refresh", 2, callCount)

            // refreshAtWallClockMs still holds the past target, refreshTimerFired=false.
            // The NEXT foreground detects the past target and fires case 2 (retry is deferred, not lost).
            manager.fireFirstStarted()

            assertEquals(
                "next foreground should detect the missed target and trigger the retry",
                3,
                callCount
            )
            verify { spyLog.info(match { it.contains("case=missed-refresh") }) }
        }

    @Test
    fun `foreground racing with fired timer only launches one scheduled refresh`() = runTest(
        dispatcher
    ) {
        val racingClock = FireOnCancelClock(TIME, ISO_TIME)
        every { Registry.clock } returns racingClock
        val provider = CountingSuccessProvider(makeJwt())
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, provider.callCount)

        val timerTask = racingClock.scheduledTasks.first()
        racingClock.time = timerTask.time + 1_000L

        manager.fireFirstStarted()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "racing timer and foreground handlers should share one provider fetch",
            2,
            provider.callCount
        )
        verify(exactly = 1) {
            spyLog.info("Proactive token refresh fired")
        }
    }

    @Test
    fun `foreground retries after scheduled timer refresh fails`() = runTest(dispatcher) {
        val initialToken = makeJwt()
        val retryToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(initialToken),
                    Result.failure(RuntimeException("refresh failed")),
                    Result.success(retryToken)
                )
            )
        )
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, provider.callCount)

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("scheduled refresh should have failed", 2, provider.callCount)

        manager.fireFirstStarted()

        assertEquals(
            "failed scheduled refresh should leave one foreground retry available",
            3,
            provider.callCount
        )
    }

    @Test
    fun `second foreground after successful retry is a no-op`() = runTest(dispatcher) {
        // After the foreground case-2 branch fires and the retry succeeds, refreshAtWallClockMs
        // is cleared before the retry coroutine launches. A subsequent foreground transition
        // therefore has no past target to act on and must be a case=still-valid no-op.
        val initialToken = makeJwt()
        val retryToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(initialToken),
                    Result.failure(RuntimeException("timer refresh failed")),
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

        // First foreground → case 2 (missed refresh), retry succeeds and schedules next refresh
        manager.fireFirstStarted()
        assertEquals("foreground retry should fire", 3, provider.callCount)
        verify(exactly = 1) { spyLog.info(match { it.contains("case=missed-refresh") }) }

        // Second foreground → case 3: refreshAtWallClockMs was cleared before the retry launched,
        // so the new refresh target (set by the retry's doFetch) is in the future — still-valid
        manager.fireFirstStarted()
        assertEquals("second foreground should be a no-op", 3, provider.callCount)
        verify(atLeast = 1) { spyLog.info(match { it.contains("case=still-valid") }) }
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

    // MARK: - Observer notification and clearTokenState

    @Test
    fun `refresh observer receives jwt on proactive refresh`() = runTest(dispatcher) {
        val jwt = makeJwt()
        val provider = CountingSuccessProvider(jwt)
        val manager = KlaviyoAuthTokenManager()

        val receivedTokens = mutableListOf<String>()
        manager.onTokenRefresh { receivedTokens.add(it) }

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            "eager fetch notifies observers with the acquired token",
            1,
            receivedTokens.size
        )

        // Fire the scheduled refresh
        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "observer should receive jwt again after proactive refresh",
            2,
            receivedTokens.size
        )
        assertEquals(jwt, receivedTokens.last())
    }

    @Test
    fun `multiple refresh observers all receive token`() = runTest(dispatcher) {
        val jwt = makeJwt()
        val provider = CountingSuccessProvider(jwt)
        val manager = KlaviyoAuthTokenManager()

        val receivedA = mutableListOf<String>()
        val receivedB = mutableListOf<String>()
        manager.onTokenRefresh { receivedA.add(it) }
        manager.onTokenRefresh { receivedB.add(it) }

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("observer A should receive jwt on eager fetch and refresh", 2, receivedA.size)
        assertEquals("observer B should receive jwt on eager fetch and refresh", 2, receivedB.size)
        assertEquals(jwt, receivedA.last())
        assertEquals(jwt, receivedB.last())
    }

    @Test
    fun `refresh observer notified on initial eager fetch`() = runTest(dispatcher) {
        val jwt = makeJwt()
        val provider = CountingSuccessProvider(jwt)
        val manager = KlaviyoAuthTokenManager()

        val received = mutableListOf<String>()
        manager.onTokenRefresh { received.add(it) }

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, provider.callCount)

        assertEquals(
            "eager fetch notifies observers once it acquires a token, so a consumer that " +
                "subscribed before the token resolved still receives it",
            listOf(jwt),
            received
        )
    }

    @Test
    fun `observer notified when initial fetch completes after interactive timeout`() =
        runTest(dispatcher) {
            // Reproduces the forms scenario: a slow initial fetch is still in flight when a form
            // subscribes and issues a short interactive-timeout fetch. That caller times out and
            // the form shows without a JWT, but the underlying fetch is not cancelled — when it
            // finally resolves, the observer must be notified so the webview can inject the token.
            val jwt = makeJwt()
            val provider = ResolvableProvider()
            val manager = KlaviyoAuthTokenManager()

            val received = mutableListOf<String>()
            manager.onTokenRefresh { received.add(it) }

            manager.registerProvider(provider)
            dispatcher.scheduler.runCurrent() // eager fetch starts and suspends on the provider

            // A form subscribes and requests the token with the short interactive budget, which
            // times out (via the shared in-flight fetch) while the provider is still pending.
            var timedOut = false
            val demandCaller = launch {
                try {
                    manager.currentToken(AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS)
                } catch (_: AuthTokenException.TimedOut) {
                    timedOut = true
                }
            }
            dispatcher.scheduler.runCurrent()
            dispatcher.scheduler.advanceTimeBy(AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS + 1)
            dispatcher.scheduler.runCurrent()
            demandCaller.join()
            assertEquals("interactive fetch should have timed out", true, timedOut)
            assertEquals("no token delivered while the fetch is still in flight", 0, received.size)

            // The provider finally responds after the timeout — observer must now be notified.
            provider.resolve(jwt)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                "observer must receive the token once the slow initial fetch completes",
                listOf(jwt),
                received
            )
            assertEquals(
                "provider invoked exactly once for the shared fetch",
                1,
                provider.callCount
            )
        }

    @Test
    fun `observer throwing does not prevent other observers`() = runTest(dispatcher) {
        val jwt = makeJwt()
        val provider = CountingSuccessProvider(jwt)
        val manager = KlaviyoAuthTokenManager()

        val receivedBySecond = mutableListOf<String>()
        manager.onTokenRefresh { throw RuntimeException("boom") }
        manager.onTokenRefresh { receivedBySecond.add(it) }

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()

        verify { spyLog.warning(any(), any()) }
        assertEquals(
            "second observer should still receive jwt on eager fetch and refresh",
            2,
            receivedBySecond.size
        )
    }

    @Test
    fun `profile invalidation during observer delivery suppresses remaining observers`() =
        runTest(dispatcher) {
            val jwt = makeJwt()
            val manager = KlaviyoAuthTokenManager()
            var firstObserverCalls = 0
            val receivedBySecond = mutableListOf<String>()

            manager.onTokenRefresh {
                firstObserverCalls++
                manager.invalidate()
            }
            manager.onTokenRefresh { receivedBySecond.add(it) }

            manager.registerProvider(CountingSuccessProvider(jwt))
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, firstObserverCalls)
            assertEquals(
                "a token invalidated during delivery must not reach later observers",
                emptyList<String>(),
                receivedBySecond
            )
        }

    @Test
    fun `slow observer does not block synchronous invalidation`() {
        every { Registry.dispatcher } returns Dispatchers.IO
        val observerStarted = CountDownLatch(1)
        val releaseObserver = CountDownLatch(1)
        val invalidationCompleted = CountDownLatch(1)
        val manager = KlaviyoAuthTokenManager()

        manager.onTokenRefresh {
            observerStarted.countDown()
            releaseObserver.await(5, TimeUnit.SECONDS)
        }
        manager.registerProvider(CountingSuccessProvider(makeJwt()))
        assertTrue("observer should begin delivery", observerStarted.await(5, TimeUnit.SECONDS))

        val invalidationThread = Thread {
            manager.invalidate()
            invalidationCompleted.countDown()
        }.apply { start() }
        val completedWithoutObserver = invalidationCompleted.await(1, TimeUnit.SECONDS)

        releaseObserver.countDown()
        invalidationThread.join(5_000L)
        manager.scope.cancel()

        assertTrue(
            "invalidate must not wait for host observer code to return",
            completedWithoutObserver
        )
    }

    @Test
    fun `offTokenRefresh removes observer`() = runTest(dispatcher) {
        val provider = CountingSuccessProvider(makeJwt())
        val manager = KlaviyoAuthTokenManager()

        var notified = false
        val observer: TokenRefreshObserver = { notified = true }
        manager.onTokenRefresh(observer)
        manager.offTokenRefresh(observer)

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("removed observer must not receive jwt", false, notified)
    }

    @Test
    fun `clearTokenState drops cached token and cancels scheduled refresh`() = runTest(dispatcher) {
        val provider = CountingSuccessProvider(makeJwt())
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, provider.callCount)
        assertEquals(
            "refresh should be scheduled after eager fetch",
            1,
            staticClock.scheduledTasks.size
        )

        // Capture the original refresh target before clearing
        val originalRefreshTarget = staticClock.scheduledTasks.first().time

        manager.clearTokenState()

        // The original scheduled task should be gone immediately after clearing
        assertEquals(
            "clearTokenState should cancel the scheduled refresh",
            0,
            staticClock.scheduledTasks.size
        )

        // Advance past the original refresh target — since the timer was cancelled, no new fetch
        staticClock.execute(originalRefreshTarget - staticClock.time + 1_000L)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            "cancelled refresh timer must not trigger an additional provider call",
            1,
            provider.callCount
        )

        // Cache should be cleared: next currentToken() re-invokes provider
        manager.currentToken()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            "clearTokenState should discard cache, forcing a new fetch",
            2,
            provider.callCount
        )
    }

    @Test
    fun `queued timer refresh cannot fetch after clearTokenState returns`() = runTest(dispatcher) {
        val provider = CountingSuccessProvider(makeJwt())
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, provider.callCount)

        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        // onRefreshTimer has authorized and queued its coroutine, but the test dispatcher has not
        // run performScheduledRefresh yet.
        manager.clearTokenState()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "teardown must invalidate background work queued before it returned",
            1,
            provider.callCount
        )
    }

    @Test
    fun `teardown during refresh scheduling cannot return superseded fetch result`() = runTest(
        dispatcher
    ) {
        val initialToken = makeJwt()
        val supersededToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
        val currentToken = makeJwt(EXP_SECONDS + 1_200, IAT_SECONDS + 1_200)
        val provider = ScriptedProvider(
            ArrayDeque(
                listOf(
                    Result.success(initialToken),
                    Result.success(supersededToken),
                    Result.success(currentToken)
                )
            )
        )
        val interceptingClock = InterceptingClock(staticClock)
        every { Registry.clock } returns interceptingClock
        val manager = KlaviyoAuthTokenManager()

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        manager.clearTokenState()

        interceptingClock.onNextSchedule = {
            runBlocking { manager.clearTokenState() }
        }
        val result = manager.currentToken()

        assertEquals(
            "the caller must retry when teardown supersedes completion",
            currentToken,
            result.rawToken
        )
        assertEquals(3, provider.callCount)
    }

    @Test
    fun `clearTokenState retains provider and observers`() = runTest(dispatcher) {
        val provider = CountingSuccessProvider(makeJwt())
        val manager = KlaviyoAuthTokenManager()

        val received = mutableListOf<String>()
        manager.onTokenRefresh { received.add(it) }

        manager.registerProvider(provider)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, provider.callCount)

        manager.clearTokenState()

        // Provider is retained: next fetch invokes it directly
        manager.currentToken()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("retained provider should serve the next fetch", 2, provider.callCount)

        // Observer is retained: fire a new proactive refresh and verify it still fires. The eager
        // fetch and the post-reset currentToken() above already notified, so assert the retained
        // observer picks up one further notification rather than an absolute count.
        val countBeforeRefresh = received.size
        val timerTask = staticClock.scheduledTasks.first()
        staticClock.execute(timerTask.time - staticClock.time)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            "retained observer should receive jwt from post-reset refresh",
            countBeforeRefresh + 1,
            received.size
        )
    }

    @Test
    fun `proactive refresh does not notify observers after clearTokenState races with fetch`() =
        runTest(dispatcher) {
            // Scenario: the scheduled refresh is in-flight when clearTokenState() runs.
            //
            // clearTokenState() synchronously retires the shared fetch identity and cancels its
            // worker. A callback that arrives afterward cannot commit or broadcast its token.
            val initialToken = makeJwt()
            val refreshedToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
            val provider = InitialThenResolvableProvider(initialToken)
            val manager = KlaviyoAuthTokenManager()

            val received = mutableListOf<String>()
            manager.onTokenRefresh { received.add(it) }

            manager.registerProvider(provider)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, provider.callCount)
            // Eager fetch notified the observer with the initial token; the stale-refresh guard
            // below must prevent any further notification beyond this baseline.
            val notificationsAfterEagerFetch = received.size

            // Fire the refresh timer — provider hangs on call #2, inFlightFetch is live
            val timerTask = staticClock.scheduledTasks.first()
            staticClock.execute(timerTask.time - staticClock.time)
            dispatcher.scheduler.runCurrent()
            assertEquals("scheduled refresh should be in-flight", 2, provider.callCount)

            // Profile reset: clear token state while the refresh is still in-flight
            manager.clearTokenState()

            // Resolve the still-running provider callback (arrives after cancellation)
            provider.resolve(refreshedToken)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                "observer must not receive a stale-profile token after clearTokenState",
                notificationsAfterEagerFetch,
                received.size
            )
            assertEquals(
                "refreshed (stale-profile) token must never be delivered",
                false,
                received.contains(refreshedToken)
            )
        }

    @Test
    fun `clearTokenState with stale expectedGeneration is a no-op after new provider registers`() =
        runTest(dispatcher) {
            // Scenario mirrors resetProfile() followed immediately by registerAuthTokenProvider():
            // invalidate() captures gen=N, registerProvider() advances gen to N+1, then the
            // async clearTokenState(gen=N) fires and must NOT wipe the new session's state.
            val initialJwt = makeJwt()
            val newJwt = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
            val provider1 = CountingSuccessProvider(initialJwt)
            val provider2 = CountingSuccessProvider(newJwt)
            val manager = KlaviyoAuthTokenManager()

            manager.registerProvider(provider1)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("first eager fetch", 1, provider1.callCount)

            // Step 1: invalidate() — simulates the sync part of resetProfile()
            val gen = manager.invalidate()

            // Step 2: registerProvider() for the new user — advances profileGeneration past gen
            manager.registerProvider(provider2)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("new provider eager fetch", 1, provider2.callCount)

            // Step 3: clearTokenState(gen) — simulates the async part of resetProfile();
            // because profileGeneration > gen, this must be a no-op.
            manager.clearTokenState(expectedGeneration = gen)

            // New session's cache is intact: currentToken() must NOT invoke provider2 again.
            manager.currentToken()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(
                "clearTokenState must not wipe new session when provider was re-registered",
                1,
                provider2.callCount
            )
        }

    @Test
    fun `invalidate causes currentToken to bypass cache and trigger new fetch`() =
        runTest(dispatcher) {
            val jwt = makeJwt()
            val provider = CountingSuccessProvider(jwt)
            val manager = KlaviyoAuthTokenManager()

            manager.registerProvider(provider)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("eager fetch on register", 1, provider.callCount)

            // Warm-cache sanity check: currentToken() should serve from cache without calling provider.
            manager.currentToken()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("cache hit — provider not called again", 1, provider.callCount)

            // invalidate() sets profileResetPending; cachedToken is still non-null at this point.
            manager.invalidate()

            // currentToken() must not return the stale cached value — falls through to a fetch.
            manager.currentToken()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("invalidate forces a new provider fetch", 2, provider.callCount)
        }

    @Test
    fun `invalidate prevents observer notification when refresh is in-flight at time of reset`() =
        runTest(dispatcher) {
            // Scenario A: performScheduledRefresh is already in-flight when resetProfile() calls
            // invalidate() synchronously. The profileResetPending flag is set while the fetch
            // is suspended; the post-fetch guard reads it and skips observer dispatch.
            val initialToken = makeJwt()
            val refreshedToken = makeJwt(EXP_SECONDS + 600, IAT_SECONDS + 600)
            val provider = InitialThenResolvableProvider(initialToken)
            val manager = KlaviyoAuthTokenManager()

            val received = mutableListOf<String>()
            manager.onTokenRefresh { received.add(it) }

            manager.registerProvider(provider)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, provider.callCount)
            // Eager fetch already notified with the initial token; the invalidate guard must
            // suppress any further notification beyond this baseline.
            val notificationsAfterEagerFetch = received.size

            // Fire the scheduled refresh; provider hangs on call #2 while fetch is suspended
            val timerTask = staticClock.scheduledTasks.first()
            staticClock.execute(timerTask.time - staticClock.time)
            dispatcher.scheduler.runCurrent()
            assertEquals("scheduled refresh is in-flight", 2, provider.callCount)

            // Synchronous invalidate() — simulates the sync step of resetProfile().
            // clearTokenState() has NOT yet run; only profileResetPending is set.
            manager.invalidate()

            // Resolve the still-running provider callback (fetch completes after invalidate)
            provider.resolve(refreshedToken)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                "observer must not fire after invalidate() even before clearTokenState runs",
                notificationsAfterEagerFetch,
                received.size
            )
        }

    @Test
    fun `invalidate prevents observer notification when refresh starts after profile reset`() =
        runTest(dispatcher) {
            // Scenario B (the gap bugbot found): the scheduled timer fires AFTER invalidate() runs.
            // performScheduledRefresh never had a "pre-invalidate" state to compare against — the
            // old captured-generation approach failed here because it captured the post-invalidate
            // generation and the check passed. profileResetPending fixes this: the flag is already
            // true when the refresh starts, so the post-fetch guard correctly skips observers.
            val jwt = makeJwt()
            val provider = CountingSuccessProvider(jwt)
            val manager = KlaviyoAuthTokenManager()

            val received = mutableListOf<String>()
            manager.onTokenRefresh { received.add(it) }

            manager.registerProvider(provider)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, provider.callCount)
            // Eager fetch already notified with the acquired token; the invalidate guard must
            // suppress any further notification beyond this baseline.
            val notificationsAfterEagerFetch = received.size

            // Synchronous invalidate() fires BEFORE the refresh timer
            manager.invalidate()

            // Now fire the refresh timer — performScheduledRefresh starts after invalidate()
            val timerTask = staticClock.scheduledTasks.first()
            staticClock.execute(timerTask.time - staticClock.time)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                "observer must not fire when refresh starts after invalidate()",
                notificationsAfterEagerFetch,
                received.size
            )
        }

    // MARK: - Helpers

    /**
     * A test [Clock] that fires a scheduled task synchronously when its [Clock.Cancellable.cancel]
     * is called, modelling the race where a timer fires at the exact instant it is being cancelled.
     * Used only by [foreground racing with fired timer only launches one scheduled refresh] to
     * verify the generation-based deduplication guard.
     *
     * Note: [Clock.Cancellable.runNow] and [Clock.Cancellable.cancel] have the same effect —
     * this is intentional; "cancel" here means "cancel the pending delay and run immediately."
     */
    private class FireOnCancelClock(
        var time: Long,
        private val formatted: String
    ) : Clock {
        val scheduledTasks = mutableListOf<ScheduledTask>()

        override fun currentTimeMillis(): Long = time

        override fun isoTime(milliseconds: Long): String = formatted

        override fun schedule(delay: Long, task: () -> Unit): Clock.Cancellable {
            val scheduledTask = ScheduledTask(currentTimeMillis() + delay, task)
            scheduledTasks.add(scheduledTask)
            return object : Clock.Cancellable {
                override fun runNow() {
                    if (scheduledTasks.remove(scheduledTask)) {
                        task()
                    }
                }

                override fun cancel(): Boolean {
                    val removed = scheduledTasks.remove(scheduledTask)
                    if (removed) {
                        task()
                    }
                    return removed
                }
            }
        }

        class ScheduledTask(val time: Long, val task: () -> Unit)
    }

    private class InterceptingClock(private val delegate: Clock) : Clock {
        var onNextSchedule: (() -> Unit)? = null

        override fun currentTimeMillis(): Long = delegate.currentTimeMillis()

        override fun isoTime(milliseconds: Long): String = delegate.isoTime(milliseconds)

        override fun schedule(delay: Long, task: () -> Unit): Clock.Cancellable {
            onNextSchedule?.also { onNextSchedule = null }?.invoke()
            return delegate.schedule(delay, task)
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

    /**
     * Captures every callback so tests can resolve them manually, modelling a provider whose
     * initial fetch stays in flight past a caller's timeout.
     */
    private class ResolvableProvider : AuthTokenProvider {
        var callCount = 0
            private set

        private val pendingCallbacks = ArrayDeque<AuthTokenProvider.Callback>()

        override fun fetchToken(callback: AuthTokenProvider.Callback) {
            callCount++
            pendingCallbacks.addLast(callback)
        }

        fun resolve(jwt: String) = pendingCallbacks.removeFirstOrNull()?.onSuccess(jwt)
    }

    private class InitialThenResolvableProvider(private val initialJwt: String) : AuthTokenProvider {
        var callCount = 0
            private set

        private val pendingCallbacks = ArrayDeque<AuthTokenProvider.Callback>()

        override fun fetchToken(callback: AuthTokenProvider.Callback) {
            callCount++
            if (callCount == 1) {
                callback.onSuccess(initialJwt)
            } else {
                pendingCallbacks.addLast(callback)
            }
        }

        fun resolve(jwt: String) = pendingCallbacks.removeFirstOrNull()?.onSuccess(jwt)
    }

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
}

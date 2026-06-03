package com.klaviyo.core.auth

import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.LifecycleMonitor
import com.klaviyo.core.safeLaunch
import com.klaviyo.core.utils.takeIf
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal class KlaviyoAuthTokenManager(
    private val lifecycleMonitor: LifecycleMonitor = Registry.lifecycleMonitor
) : AuthTokenManager {

    // Internal (not on the interface) so MAGE-619 consumers are forced to use their own scope
    // when calling currentToken(), binding auth work to the correct lifecycle.
    internal val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Registry.dispatcher)

    init {
        lifecycleMonitor.onActivityEvent(::onLifecycleEvent)
    }

    // Guards the read-validate-write transition on both cachedToken and inFlightFetch, ensuring
    // exactly one Deferred is created when multiple callers miss the cache simultaneously.
    private val mutex = Mutex()

    // @Volatile so reads in invokeProvider (outside the mutex) always observe the latest write
    // from registerProvider. Single-write-wins semantics are acceptable for the happy path.
    @Volatile private var provider: AuthTokenProvider? = null

    @Volatile private var cachedToken: ValidatedToken? = null

    // Shared in-flight fetch deferred. All concurrent callers that miss the cache await this
    // single Deferred rather than each invoking the provider independently. Cleared (via
    // invokeOnCompletion) on both success and failure so the next request starts a fresh fetch.
    // @Volatile because registerProvider and invokeOnCompletion clear it without holding the
    // mutex; @Volatile ensures those writes are visible to the mutex-protected read in currentToken.
    @Volatile private var inFlightFetch: Deferred<ValidatedToken>? = null

    // NOT cleared in the timer callback on firing — a failed refresh leaves this pointing at a
    // past target so handleForegroundTransition() case 2 can detect the miss and retry once.
    @Volatile private var refreshJob: Clock.Cancellable? = null

    @Volatile private var refreshAtWallClockMs: Long? = null

    // Set by the timer callback while holding [mutex] before refresh work begins. This prevents a
    // foreground transition from treating an already-fired timer as a Doze-style miss while the
    // scheduled refresh coroutine is still queued or in-flight.
    @Volatile private var refreshTimerFired = false

    // Monotonic token used to ignore callbacks from refresh jobs cancelled by a later schedule.
    @Volatile private var refreshGeneration = 0L

    // Reserved for MAGE-630 (onTokenRefresh/offTokenRefresh observers). Matches the established
    // SDK observer-collection pattern (CopyOnWriteArrayList for thread-safe iteration while
    // observers add/remove themselves on arbitrary threads).
    @Suppress("unused")
    private val refreshObservers = CopyOnWriteArrayList<TokenRefreshObserver>()

    override fun registerProvider(provider: AuthTokenProvider) {
        // Cancel any in-flight fetch for the old provider before swapping.
        // Two complementary guards prevent a stale token from reaching the cache:
        //   1. If the cancelled coroutine is still inside invokeProvider(), the isActive check in
        //      suspendCancellableCoroutine drops any late onSuccess/onFailure callback.
        //   2. If the callback already fired and doFetch() is past invokeProvider() but hasn't
        //      written the cache yet, ensureActive() in doFetch will throw CancellationException
        //      before the write — even when mutex.withLock acquires the lock uncontended.
        inFlightFetch?.cancel()
        inFlightFetch = null
        refreshJob?.cancel()
        refreshJob = null
        refreshAtWallClockMs = null
        refreshTimerFired = false
        refreshGeneration += 1
        cachedToken = null
        this.provider = provider
        Registry.log.info("AuthTokenProvider registered")
        scope.safeLaunch { tryEagerFetch() }
    }

    private suspend fun tryEagerFetch() {
        try {
            currentToken(AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS)
        } catch (e: CancellationException) {
            // Preserve structured concurrency by rethrowing cancellation.
            throw e
        } catch (_: Exception) {
            // The failure is already logged at ERROR by validateOrThrow, by the timeout path, or
            // surfaced by the provider's own onFailure. Nothing more to log here.
        }
    }

    override suspend fun currentToken(timeoutMs: Long): ValidatedToken {
        return currentTokenInternal(timeoutMs = timeoutMs, allowCachedToken = true)
    }

    private suspend fun currentTokenInternal(
        timeoutMs: Long,
        allowCachedToken: Boolean
    ): ValidatedToken {
        require(timeoutMs > 0L) { "timeoutMs must be positive, but was $timeoutMs" }
        if (provider == null) throw AuthTokenException.NoProviderRegistered

        if (allowCachedToken) {
            // Optimistic read of @Volatile field — no lock needed for the fast path.
            val cached = cachedToken
            if (cached != null && isStillValid(cached)) return cached
        }

        // Atomic read-or-create of the in-flight deferred. The mutex ensures exactly one
        // scope.async { } is launched when multiple callers miss the cache simultaneously.
        val deferred: Deferred<ValidatedToken> = mutex.withLock {
            // Re-check under the lock; a concurrent caller may have populated the cache while
            // we waited. Non-local return from this inline lambda exits currentTokenInternal()
            // directly.
            val freshenedCache = cachedToken
            if (allowCachedToken && freshenedCache != null && isStillValid(freshenedCache)) {
                return freshenedCache
            }

            inFlightFetch ?: scope.async { doFetch() }.also { d ->
                inFlightFetch = d
                // Reference-identity check: prevents a stale deferred's completion handler from
                // clearing a freshly-created deferred after a concurrent provider swap.
                d.invokeOnCompletion { if (inFlightFetch === d) inFlightFetch = null }
            }
        }

        // Each caller races its own timeout budget against the shared deferred. Timing out does
        // NOT cancel the underlying task — other callers with a larger budget still benefit if
        // the provider eventually responds.
        //
        // CancellationException handling: Deferred.await() throws CancellationException if the
        // deferred is cancelled externally (e.g. by registerProvider swapping the provider). This
        // would otherwise propagate to callers as if THEIR coroutine was cancelled, which breaks
        // structured-concurrency semantics. We catch it, use ensureActive() to distinguish "our
        // coroutine was cancelled" (rethrow — normal teardown) from "the deferred was cancelled
        // by a provider swap" (retry — pick up the new provider's fetch transparently).
        return withTimeoutOrNull(timeoutMs) {
            try {
                deferred.await()
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                currentTokenInternal(timeoutMs = timeoutMs, allowCachedToken = allowCachedToken)
            }
        } ?: run {
            val error = AuthTokenException.TimedOut
            Registry.log.warning(requireNotNull(error.message), error)
            throw error
        }
    }

    /**
     * Invoke the provider, validate the returned JWT, write to the cache, and return the token.
     * Runs inside [scope].async so failures (provider error, validation error) are captured by
     * the Deferred and re-thrown to all awaiting callers.
     */
    private suspend fun doFetch(): ValidatedToken {
        val jwt = invokeProvider()
        val token = validateOrThrow(jwt)
        // Non-suspending cancellation check: if this deferred was cancelled (e.g. by a provider
        // swap) after invokeProvider() returned but before we write the cache, bail out now.
        // mutex.withLock does NOT check cancellation when the lock is uncontended, so this guard
        // is required even when the mutex is free.
        currentCoroutineContext().ensureActive()
        mutex.withLock {
            cachedToken = token
            scheduleRefresh(token)
        }
        Registry.log.info(
            "Auth token acquired (exp=${token.expiresAtEpochSeconds}, iat=${token.issuedAtEpochSeconds})"
        )
        return token
    }

    private suspend fun invokeProvider(): String = suspendCancellableCoroutine { continuation ->
        val callback = object : AuthTokenProvider.Callback {
            override fun onSuccess(jwt: String) {
                if (continuation.isActive) continuation.resume(jwt)
            }

            override fun onFailure(error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
        provider?.fetchToken(callback) ?: continuation.resumeWithException(
            AuthTokenException.NoProviderRegistered
        )
    }

    private fun validateOrThrow(jwt: String): ValidatedToken =
        when (val result = JWTParser.parseAndValidate(jwt)) {
            is JWTValidationResult.Valid -> result.token
            else -> {
                val reason = result::class.simpleName ?: "Unknown"
                val error = AuthTokenException.ValidationFailed(reason)
                Registry.log.error(requireNotNull(error.message), error)
                throw error
            }
        }

    private fun isStillValid(token: ValidatedToken): Boolean {
        val now = Registry.clock.currentTimeMillis() / 1000L
        return now < token.expiresAtEpochSeconds - JWTParser.DEFAULT_LEEWAY_SECONDS
    }

    // Called under mutex (from doFetch). Non-suspending; safe inside withLock.
    private fun scheduleRefresh(token: ValidatedToken) {
        val nowMs = Registry.clock.currentTimeMillis()
        val targetMs = computeRefreshTarget(token, nowMs)
        val generation = refreshGeneration + 1
        refreshJob?.cancel()
        refreshGeneration = generation
        refreshTimerFired = false
        refreshAtWallClockMs = targetMs
        refreshJob = Registry.clock.schedule((targetMs - nowMs).coerceAtLeast(0)) {
            scope.safeLaunch {
                if (markRefreshTimerFired(generation)) {
                    performScheduledRefresh(generation)
                }
            }
        }
        Registry.log.info(
            "Proactive token refresh scheduled (target=${Registry.clock.isoTime(targetMs)})"
        )
    }

    private suspend fun markRefreshTimerFired(generation: Long): Boolean =
        mutex.withLock {
            if (refreshGeneration != generation) return@withLock false
            refreshTimerFired = true
            true
        }

    // Forces a fresh provider invocation and routes through the standard dedup + timeout path.
    // Leaves the existing cache intact so callers can keep using it while refresh is in-flight,
    // even if the refresh attempt fails. Any
    // concurrent caller that arrives while refresh is in-flight shares the
    // single in-flight Deferred automatically.
    // Logs at WARNING on failure — the still-valid cached token remains for live consumers.
    // On failure does NOT reschedule; one foreground-transition retry is possible if
    // refreshAtWallClockMs was not yet cleared (timer fired but fetch failed).
    // TODO (MAGE-630): emit onTokenRefresh notification to observers on success.
    private suspend fun performScheduledRefresh(timerGeneration: Long? = null) {
        if (provider == null) return
        Registry.log.info("Proactive token refresh fired")
        try {
            currentTokenInternal(
                timeoutMs = AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS,
                allowCachedToken = false
            )
            Registry.log.info("Proactive token refresh succeeded")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (timerGeneration != null) clearFiredFlagForFailedRefresh(timerGeneration)
            Registry.log.warning("Proactive token refresh failed: ${e.javaClass.simpleName}", e)
        }
    }

    private suspend fun clearFiredFlagForFailedRefresh(timerGeneration: Long) {
        mutex.withLock {
            if (refreshGeneration == timerGeneration) {
                refreshTimerFired = false
            }
        }
    }

    private fun onLifecycleEvent(event: ActivityEvent) {
        event.takeIf<ActivityEvent.FirstStarted>() ?: return
        scope.safeLaunch { handleForegroundTransition() }
    }

    // Reconciles cache and scheduled-refresh state on foreground transition.
    // safeLaunch is non-suspending, so the mutex is released before any launched
    // coroutine runs — no re-entrancy risk with currentToken()'s own withLock call.
    private suspend fun handleForegroundTransition() {
        val nowMs = Registry.clock.currentTimeMillis()
        mutex.withLock {
            val cached = cachedToken
            val targetMs = refreshAtWallClockMs
            when {
                cached != null && !isStillValid(cached) -> {
                    cachedToken = null
                    refreshJob?.cancel()
                    refreshJob = null
                    refreshAtWallClockMs = null
                    refreshTimerFired = false
                    refreshGeneration += 1
                    Registry.log.info(
                        "AuthTokenManager: foreground transition (case=expired-cached-token)"
                    )
                    scope.safeLaunch { tryEagerFetch() }
                }
                targetMs != null && nowMs >= targetMs && !refreshTimerFired -> {
                    refreshJob?.cancel()
                    refreshJob = null
                    refreshAtWallClockMs = null
                    refreshTimerFired = false
                    refreshGeneration += 1
                    Registry.log.info(
                        "AuthTokenManager: foreground transition (case=missed-refresh)"
                    )
                    scope.safeLaunch { performScheduledRefresh() }
                }
                else -> Registry.log.info(
                    "AuthTokenManager: foreground transition (case=still-valid)"
                )
            }
        }
    }

    companion object {
        /**
         * Computes the absolute wall-clock target (epoch ms) for the next proactive refresh.
         *
         * Ideal: iat + 0.9 * (exp - iat). Clamped to [now + 5s, exp - leeway]:
         * - Upper bound: refresh fires before the token is considered stale.
         * - Lower bound: prevents tight loops for tokens issued near their own expiry.
         */
        internal fun computeRefreshTarget(token: ValidatedToken, nowMs: Long): Long {
            val iatMs = token.issuedAtEpochSeconds * 1000L
            val expMs = token.expiresAtEpochSeconds * 1000L
            val idealMs = iatMs + (0.9 * (expMs - iatMs)).toLong()
            val upperBoundMs = expMs - JWTParser.DEFAULT_LEEWAY_SECONDS * 1000L
            val lowerBoundMs = nowMs + 5_000L
            return maxOf(lowerBoundMs, minOf(idealMs, upperBoundMs))
        }
    }
}

package com.klaviyo.core.auth

import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.LifecycleMonitor
import com.klaviyo.core.safeLaunch
import com.klaviyo.core.utils.takeIf
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
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
    // @Volatile because registerProvider writes without holding the mutex; @Volatile ensures those
    // writes are visible to subsequent mutex-protected reads in handleForegroundTransition().
    @Volatile private var refreshJob: Clock.Cancellable? = null

    // @Volatile for the same reason as refreshJob: registerProvider clears without holding mutex.
    @Volatile private var refreshAtWallClockMs: Long? = null

    // Set by the timer callback while holding mutex before refresh work begins. This prevents a
    // foreground transition from treating an already-fired timer as a Doze-style miss while the
    // scheduled refresh coroutine is still queued or in-flight.
    // @Volatile because registerProvider resets without holding the mutex.
    @Volatile private var refreshTimerFired = false

    // Monotonic token used to ignore callbacks from refresh jobs cancelled by a later schedule.
    // AtomicLong rather than @Volatile Long so that registerProvider's non-mutex increment
    // (refreshGeneration.incrementAndGet()) is truly atomic and cannot race with scheduleRefresh's
    // mutex-held increment to produce a lost update.
    private val refreshGeneration = AtomicLong(0L)

    // Tracks profile lifecycle events: registerProvider(), invalidate(), and clearTokenState().
    // Used by clearTokenState(expectedGeneration) to detect whether registerProvider() ran between
    // the invalidate() call and the async clear, so a late clear doesn't wipe the new session.
    // Deliberately separate from refreshGeneration (which scheduleRefresh() also bumps).
    private val profileGeneration = AtomicLong(0L)

    // Set to true by invalidate() and reset to false by registerProvider() and clearTokenState().
    // Read by performScheduledRefresh just before notifying observers: if a profile reset is
    // pending (i.e. invalidate() was called but clearTokenState() hasn't finished yet), the
    // refresh must not broadcast the now-stale token. @Volatile because it is written on the
    // calling thread (main) and read on the dispatcher (IO) with no other synchronisation.
    @Volatile private var profileResetPending = false

    // CopyOnWriteArrayList for thread-safe iteration while observers add/remove on arbitrary threads
    // (established SDK observer-collection pattern, matches StateChangeObserver, ActivityObserver).
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
        refreshGeneration.incrementAndGet()
        // Advance profileGeneration so any pending clearTokenState(expectedGeneration) from a
        // prior resetProfile() sees the generation mismatch and skips, preserving this new
        // session's token state.
        profileGeneration.incrementAndGet()
        // Clear the reset-pending flag: the new provider supersedes any in-progress logout reset.
        profileResetPending = false
        cachedToken = null
        this.provider = provider
        Registry.log.info("AuthTokenProvider registered")
        scope.safeLaunch { tryEagerFetch() }
    }

    override fun onTokenRefresh(observer: TokenRefreshObserver) {
        refreshObservers.add(observer)
    }

    override fun offTokenRefresh(observer: TokenRefreshObserver) {
        refreshObservers.remove(observer)
    }

    override fun invalidate(): Long {
        // Set the flag before bumping the generation so that any performScheduledRefresh that
        // reads the flag after this call (regardless of when its fetch started) will skip observers.
        profileResetPending = true
        return profileGeneration.incrementAndGet()
    }

    override suspend fun clearTokenState(expectedGeneration: Long) {
        var cleared = false
        mutex.withLock {
            // If the caller captured a generation via invalidate() and a new provider has since
            // been registered (profileGeneration advanced), skip the clear to avoid wiping the
            // new session's token cache and refresh schedule.
            if (expectedGeneration >= 0L && profileGeneration.get() != expectedGeneration) {
                Registry.log.verbose(
                    "clearTokenState: skipped — provider re-registered since reset"
                )
                return@withLock
            }
            inFlightFetch?.cancel()
            inFlightFetch = null
            refreshJob?.cancel()
            refreshJob = null
            refreshAtWallClockMs = null
            refreshTimerFired = false
            refreshGeneration.incrementAndGet()
            // TODO (MAGE-684): connectivityWaitJob?.cancel(); connectivityWaitJob = null
            cachedToken = null
            profileGeneration.incrementAndGet()
            // Clear the reset-pending flag so the next successful refresh (from a new or retained
            // provider) can notify observers normally.
            profileResetPending = false
            cleared = true
        }
        if (cleared) Registry.log.info("Token state cleared")
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
        return getOrFetchToken(timeoutMs = timeoutMs, allowCachedToken = true)
    }

    /**
     * Shared implementation behind [currentToken]. Split out so the
     * `allowCachedToken` knob stays off the public [AuthTokenManager] interface —
     * external callers always get the cache-honoring behavior.
     *
     * @param allowCachedToken When `true` (the [currentToken] path), a still-valid
     *   cached token short-circuits both the optimistic pre-lock read and the
     *   double-checked read under the mutex. When `false`, both reads are skipped
     *   and the call always resolves through the in-flight fetch, forcing a fresh
     *   provider invocation even if the cache is currently valid.
     *
     *   Only the proactive-refresh path ([performScheduledRefresh]) passes `false`:
     *   a refresh fires *because* the cached token is aging, so returning that
     *   still-valid token would make the refresh a no-op. Note this only bypasses
     *   the *read* — dedup still applies (a concurrent fetch is joined, not
     *   duplicated, via [inFlightFetch]), and the existing cache is left intact so
     *   demand callers keep getting the valid token while the refresh runs.
     */
    private suspend fun getOrFetchToken(
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
        //
        // Budget: the retry calls currentToken(timeoutMs) inside this same withTimeoutOrNull block,
        // so the caller's original deadline governs the total wait end-to-end. The recursive call
        // creates a fresh inner withTimeoutOrNull(timeoutMs) starting from the current time, but
        // the outer one fires first if the budget is nearly exhausted. This is intentional — the
        // caller asked for a response within timeoutMs of their call site, not of the swap.
        return withTimeoutOrNull(timeoutMs) {
            try {
                deferred.await()
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                getOrFetchToken(timeoutMs = timeoutMs, allowCachedToken = allowCachedToken)
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

    /**
     * Called under mutex (from [doFetch]). Non-suspending; safe inside withLock.
     */
    private fun scheduleRefresh(token: ValidatedToken) {
        val nowMs = Registry.clock.currentTimeMillis()
        val targetMs = computeRefreshTarget(token, nowMs)
        // Bump the generation before cancelling so that if cancel() synchronously fires the old
        // task (e.g. FireOnCancelClock in tests), the task sees a stale generation and self-aborts.
        val generation = refreshGeneration.incrementAndGet()
        refreshJob?.cancel()
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
            if (refreshGeneration.get() != generation) return@withLock false
            refreshTimerFired = true
            true
        }

    /**
     * Forces a fresh provider invocation and routes through the standard dedup + timeout path.
     * Leaves the existing cache intact so callers can keep using it while refresh is in-flight,
     * even if the refresh attempt fails; any concurrent caller that arrives while refresh is
     * in-flight shares the single in-flight Deferred automatically.
     *
     * On success, notifies registered [TokenRefreshObserver]s with the new JWT, subject to two
     * guards: (1) the returned token is still the live cached value (clears can null the cache
     * mid-flight), and (2) no profile reset is pending ([profileResetPending] is false). The
     * reset-pending flag is set synchronously by [invalidate] so that any refresh completing
     * in the window between [invalidate] and [clearTokenState] is suppressed. Dispatch is
     * best-effort — see [notifyRefreshObservers].
     *
     * Logs at WARNING on failure — the still-valid cached token remains for live consumers.
     * On failure does NOT reschedule; one foreground-transition retry is possible if
     * [refreshAtWallClockMs] was not yet cleared (timer fired but fetch failed).
     */
    private suspend fun performScheduledRefresh(timerGeneration: Long? = null) {
        if (provider == null) return
        Registry.log.info("Proactive token refresh fired")
        try {
            val token = getOrFetchToken(
                timeoutMs = AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS,
                allowCachedToken = false
            )
            // Two-part stale guard before notifying observers:
            // 1. Cache check (@Volatile read): clearTokenState() may have nulled cachedToken
            //    while the fetch was suspended; skip if this token is no longer the live value.
            // 2. Reset-pending check (@Volatile read): invalidate() sets this flag synchronously
            //    from resetProfile() before the async clearTokenState() runs. Any refresh that
            //    completes while a logout reset is pending must not broadcast to observers —
            //    regardless of whether the refresh started before or after invalidate() was called.
            //    The flag is cleared by clearTokenState() and registerProvider().
            // Dispatch is best-effort; the small TOCTOU window on these volatile reads is
            // acceptable (see notifyRefreshObservers KDoc).
            if (cachedToken?.rawToken == token.rawToken && !profileResetPending) {
                notifyRefreshObservers(token.rawToken)
            }
            Registry.log.info("Proactive token refresh succeeded")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (timerGeneration != null) clearFiredFlagForFailedRefresh(timerGeneration)
            Registry.log.warning("Proactive token refresh failed: ${e.javaClass.simpleName}", e)
        }
    }

    /**
     * Iterates [refreshObservers] and invokes each with [jwt]. Best-effort: if an observer throws,
     * the exception is logged at WARNING and the remaining observers are still called.
     */
    private fun notifyRefreshObservers(jwt: String) {
        refreshObservers.forEach { observer ->
            try {
                observer(jwt)
            } catch (e: CancellationException) {
                // Structured-concurrency contract: CancellationException must never be swallowed.
                throw e
            } catch (e: Throwable) {
                // Best-effort dispatch: log and continue so a misbehaving observer cannot block
                // others from receiving the token. Catching Throwable (not just Exception) ensures
                // an observer throwing an Error (e.g. AssertionError) also doesn't escape the loop.
                Registry.log.warning(
                    "TokenRefreshObserver threw ${e.javaClass.simpleName} — skipping",
                    e
                )
            }
        }
    }

    private suspend fun clearFiredFlagForFailedRefresh(timerGeneration: Long) {
        mutex.withLock {
            if (refreshGeneration.get() == timerGeneration) {
                refreshTimerFired = false
            }
        }
    }

    private fun onLifecycleEvent(event: ActivityEvent) {
        event.takeIf<ActivityEvent.FirstStarted>() ?: return
        scope.safeLaunch { handleForegroundTransition() }
    }

    /**
     * Reconciles cache and scheduled-refresh state on foreground transition.
     * [safeLaunch] is non-suspending, so the mutex is released before any launched
     * coroutine runs — no re-entrancy risk with [currentToken]'s own withLock call.
     *
     * Case 1 uses [tryEagerFetch] (`allowCachedToken = true`) because [cachedToken] is explicitly
     * nulled before the launch, guaranteeing a cache miss without needing to bypass the cache.
     * Case 2 uses [performScheduledRefresh] (`allowCachedToken = false`) because the cached token
     * is still valid and must NOT be returned — we need a fresh provider call despite the hit.
     */
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
                    refreshGeneration.incrementAndGet()
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
                    refreshGeneration.incrementAndGet()
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

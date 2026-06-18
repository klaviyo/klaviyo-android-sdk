package com.klaviyo.core.auth

import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.LifecycleMonitor
import com.klaviyo.core.networking.NetworkObserver
import com.klaviyo.core.safeLaunch
import com.klaviyo.core.utils.takeIf
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
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

    // Tracks logout/reset events only: incremented by invalidate() and clearTokenState(), but NOT
    // by registerProvider(). Used by shouldArmConnectivityRetry to distinguish a stale failure from
    // a logout-triggered reset vs. a benign mid-fetch provider swap. profileGeneration is too coarse
    // for this check because registerProvider() also bumps it, which would wrongly block
    // connectivity retry for the new session when a provider is swapped mid-fetch.
    private val resetGeneration = AtomicLong(0L)

    // Set to true by invalidate() and reset to false by registerProvider() and clearTokenState().
    // Read by performScheduledRefresh just before notifying observers: if a profile reset is
    // pending (i.e. invalidate() was called but clearTokenState() hasn't finished yet), the
    // refresh must not broadcast the now-stale token. @Volatile because it is written on the
    // calling thread (main) and read on the dispatcher (IO) with no other synchronisation.
    @Volatile private var profileResetPending = false

    // CopyOnWriteArrayList for thread-safe iteration while observers add/remove on arbitrary threads
    // (established SDK observer-collection pattern, matches StateChangeObserver, ActivityObserver).
    private val refreshObservers = CopyOnWriteArrayList<TokenRefreshObserver>()

    // A pending coroutine that waits for connectivity to be restored before retrying
    // performScheduledRefresh. At most one is active at a time. All transitions to this field and
    // to connectivityWaitGeneration are serialized via connectivityWaitLock. @Volatile for
    // visibility to tests that read the field outside any lock.
    // Internal (not private) so tests in this module can inspect job state without reflection.
    @Volatile internal var connectivityWaitJob: Job? = null

    // Monotonic counter bumped each time armConnectivityWaitJob() arms a new job. Used by the
    // job's finally block to detect whether a newer arm replaced it; if so the old job must not
    // null out the new job's reference. AtomicLong for the same reason as refreshGeneration.
    private val connectivityWaitGeneration = AtomicLong(0L)

    // JVM lock serializing all connectivityWaitJob + connectivityWaitGeneration transitions.
    // Acquired without holding mutex (armConnectivityWaitJob) and while holding mutex
    // (clearTokenState). Lock ordering is always: mutex → connectivityWaitLock.
    private val connectivityWaitLock = Any()

    private fun cancelConnectivityWaitJob() {
        synchronized(connectivityWaitLock) {
            connectivityWaitJob?.cancel()
            connectivityWaitJob = null
        }
    }

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
        cancelConnectivityWaitJob()
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

    override fun unregisterProvider() {
        // Mirror registerProvider's synchronous teardown path, but null out the provider rather
        // than setting a new one, and skip the eager fetch. All @Volatile writes here are safe
        // without the mutex because they only need to be visible, not read-modify-written atomically.
        inFlightFetch?.cancel()
        inFlightFetch = null
        refreshJob?.cancel()
        refreshJob = null
        refreshAtWallClockMs = null
        refreshTimerFired = false
        cancelConnectivityWaitJob()
        refreshGeneration.incrementAndGet()
        // Advance profileGeneration so any pending clearTokenState(expectedGeneration) from a
        // prior resetProfile() sees the generation mismatch and skips — the state was already
        // cleared here.
        profileGeneration.incrementAndGet()
        // Bump resetGeneration to signal a logout-like event: any performScheduledRefresh that
        // was in-flight when this ran will see a generation mismatch in shouldArmConnectivityRetry
        // and skip arming a connectivity retry for the now-cleared session.
        resetGeneration.incrementAndGet()
        // Clear the reset-pending flag in case invalidate() was called just before unregister.
        profileResetPending = false
        cachedToken = null
        provider = null
        Registry.log.info("AuthTokenProvider unregistered")
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
        // Also bump resetGeneration so shouldArmConnectivityRetry can detect a logout-triggered
        // failure even after clearTokenState() has cleared profileResetPending.
        resetGeneration.incrementAndGet()
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
            cancelConnectivityWaitJob()
            cachedToken = null
            profileGeneration.incrementAndGet()
            // Also advance resetGeneration so that any performScheduledRefresh that started before
            // this clear cannot arm a zombie connectivity job even when profileResetPending has been
            // cleared by the time its catch block executes.
            resetGeneration.incrementAndGet()
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
            // Optimistic read of @Volatile fields — no lock needed for the fast path.
            // Skip the cache while a profile reset is pending: invalidate() has fired but
            // clearTokenState() hasn't run yet, so cachedToken still holds the outgoing JWT.
            usableCachedToken(cachedToken)?.let { return it }
        }

        // Atomic read-or-create of the in-flight deferred. The mutex ensures exactly one
        // scope.async { } is launched when multiple callers miss the cache simultaneously.
        val deferred: Deferred<ValidatedToken> = mutex.withLock {
            // Re-check under the lock; a concurrent caller may have populated the cache while
            // we waited. Non-local return exits getOrFetchToken() directly.
            if (allowCachedToken) usableCachedToken(cachedToken)?.let { return it }

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

    /**
     * Returns [token] if it is non-null, still valid per [isStillValid], and no profile reset is
     * pending; otherwise returns `null`. Centralizes the cache-eligibility gate used in both the
     * optimistic pre-lock read and the mutex-protected double-check inside [getOrFetchToken].
     */
    private fun usableCachedToken(token: ValidatedToken?): ValidatedToken? =
        if (token != null && isStillValid(token) && !profileResetPending) token else null

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
    private suspend fun performScheduledRefresh(
        timerGeneration: Long? = null,
        allowImmediateConnectivityRetry: Boolean = true
    ) {
        // Snapshot the reset generation before suspending into the network fetch. If a logout or
        // token-state clear (invalidate / clearTokenState) fires while the fetch is in progress,
        // the catch block must not arm a connectivity retry for the now-stale session. We use
        // resetGeneration rather than profileGeneration so that a benign provider swap
        // (registerProvider without a logout) does not falsely suppress the retry for the new
        // session — registerProvider does not bump resetGeneration.
        val resetGenerationAtStart = resetGeneration.get()
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
            if (shouldArmConnectivityRetry(e, resetGenerationAtStart)) {
                armConnectivityWaitJob(
                    resumeImmediatelyIfConnected = allowImmediateConnectivityRetry
                )
            }
        }
    }

    /**
     * Returns true when a proactive-refresh failure should trigger a connectivity wait job.
     *
     * All four conditions must hold:
     * - [resetGeneration] matches [resetGenerationAtStart]: no logout or state-clear
     *   (invalidate / clearTokenState) occurred while the refresh was suspended. We use
     *   [resetGeneration] rather than [profileGeneration] so that a benign provider swap
     *   (registerProvider without a logout) does not falsely suppress retry — registerProvider
     *   does not bump [resetGeneration].
     * - The exception is a transient network error ([isNetworkException]): server errors and
     *   validation failures should not trigger a connectivity retry.
     * - [provider] is still set: a concurrent teardown may have cleared it.
     * - No profile reset is pending ([profileResetPending]): guards against the window between
     *   invalidate() and clearTokenState() where the flag is still true.
     */
    private fun shouldArmConnectivityRetry(
        exception: Exception,
        resetGenerationAtStart: Long
    ): Boolean =
        resetGeneration.get() == resetGenerationAtStart &&
            isNetworkException(exception) &&
            provider != null &&
            !profileResetPending

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
            } catch (e: Exception) {
                // Best-effort dispatch: log and continue so a misbehaving observer cannot block
                // others from receiving the token. JVM-fatal Errors (OOM, StackOverflowError, etc.)
                // are intentionally NOT caught here — they should propagate.
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

    /**
     * Returns `true` for exceptions that indicate genuine offline conditions.
     * [UnknownHostException], [SocketTimeoutException], and [ConnectException] are all subtypes of
     * [IOException]; they are listed explicitly to document the specific failure modes that warrant
     * a connectivity-driven retry. HTTP errors, validation failures, and server-side bugs return
     * `false` and must not trigger a connectivity retry (they won't resolve by waiting for the
     * network).
     */
    private fun isNetworkException(e: Exception): Boolean =
        e is UnknownHostException ||
            e is SocketTimeoutException ||
            e is ConnectException ||
            e is IOException

    /**
     * Arms [connectivityWaitJob]: a single coroutine on [scope] that suspends until the next
     * "connectivity available" notification from [Registry.networkMonitor], then triggers a
     * proactive refresh via [performScheduledRefresh].
     *
     * At most one job is active at a time. All [connectivityWaitJob] and
     * [connectivityWaitGeneration] transitions are serialized via [connectivityWaitLock] so that
     * concurrent calls (rapid flap) and racing teardown from [registerProvider]/[clearTokenState]
     * cannot leave multiple active jobs or a stale assignment.
     *
     * @param resumeImmediatelyIfConnected When true (the default), the job resumes immediately if
     * the device is already connected — [Registry.networkMonitor] does not replay state on observer
     * registration. Pass false from the connectivity-retry path to prevent a tight loop: if the
     * provider keeps failing with a network exception while the device stays online, a re-armed job
     * with [resumeImmediatelyIfConnected]=false waits for an actual connectivity transition before
     * retrying again.
     *
     * Only invoked from the proactive-refresh failure path ([performScheduledRefresh]); demand
     * callers via [currentToken] are not retried here — they surface the error to their own caller.
     */
    private fun armConnectivityWaitJob(resumeImmediatelyIfConnected: Boolean = true) {
        Registry.log.info(
            "AuthTokenManager: network failure — waiting for connectivity to retry refresh"
        )
        // Serialize cancel → generation increment → launch → assign under one lock so that
        // concurrent calls to armConnectivityWaitJob() and racing registerProvider()/
        // clearTokenState() cannot produce multiple active jobs or a stale null-out.
        val waitGeneration: Long
        synchronized(connectivityWaitLock) {
            connectivityWaitJob?.cancel()
            waitGeneration = connectivityWaitGeneration.incrementAndGet()
            connectivityWaitJob = scope.safeLaunch {
                try {
                    suspendCancellableCoroutine { continuation ->
                        val resumed = AtomicBoolean(false)
                        // Use a ref box so the lambda can capture and de-register itself.
                        val observerRef = arrayOfNulls<NetworkObserver>(1)
                        val observer: NetworkObserver = { isConnected ->
                            // One-shot guard: AtomicBoolean ensures exactly one connectivity
                            // event (including the immediate check below) resumes the coroutine.
                            if (isConnected && resumed.compareAndSet(false, true)) {
                                observerRef[0]?.let { Registry.networkMonitor.offNetworkChange(it) }
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                        }
                        observerRef[0] = observer
                        Registry.networkMonitor.onNetworkChange(observer)
                        // Install cancellation handler AFTER registration so the handler can
                        // never try to remove an observer that hasn't been registered yet.
                        continuation.invokeOnCancellation {
                            Registry.networkMonitor.offNetworkChange(observer)
                        }
                        // Resume immediately if already online — networkMonitor does not replay
                        // state on registration (handles SocketTimeoutException on live network).
                        // Skip on re-arm (resumeImmediatelyIfConnected=false) to prevent a tight
                        // loop when the provider keeps failing with IOException while the device
                        // stays connected; in that case we wait for an actual connectivity event.
                        if (resumeImmediatelyIfConnected &&
                            Registry.networkMonitor.isNetworkConnected() &&
                            resumed.compareAndSet(false, true)
                        ) {
                            Registry.networkMonitor.offNetworkChange(observer)
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                    Registry.log.info(
                        "AuthTokenManager: connectivity restored — retrying proactive refresh"
                    )
                    // Cancellation checkpoint: registerProvider()/clearTokenState() may have
                    // cancelled this job after the connectivity event fired but before we retry.
                    currentCoroutineContext().ensureActive()
                    // Pass allowImmediateConnectivityRetry=false so that if this retry also fails
                    // with a network exception, the re-armed job will NOT immediately resume on an
                    // already-connected device — avoiding a tight retry loop.
                    performScheduledRefresh(allowImmediateConnectivityRetry = false)
                } finally {
                    // Only clear the field if the generation still matches — a concurrent
                    // re-arm may have replaced this job; if so leave the new job in place.
                    synchronized(connectivityWaitLock) {
                        if (connectivityWaitGeneration.get() == waitGeneration) {
                            connectivityWaitJob = null
                        }
                    }
                }
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

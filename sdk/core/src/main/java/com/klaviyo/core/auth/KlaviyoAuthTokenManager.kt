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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Serializes every auth-token state transition behind [stateLock]. Provider calls, timers, and
 * connectivity waits run outside the critical section and report their results back with the
 * generation they started in. This keeps the public synchronous lifecycle methods synchronous
 * without spreading coordination across mutexes, volatile fields, and independent atomics.
 */
internal class KlaviyoAuthTokenManager(
    private val lifecycleMonitor: LifecycleMonitor = Registry.lifecycleMonitor
) : AuthTokenManager {

    // Internal (not on the interface) so Forms consumers bind currentToken() to their lifecycle.
    // Tests also cancel this scope to verify teardown behavior.
    internal val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Registry.dispatcher)

    private val stateLock = Any()
    private val completionBarrier = Any()
    private val observerDispatchLock = Any()
    private val state = State()

    internal val connectivityWaitJob: Job?
        get() = synchronized(stateLock) { state.connectivityWait?.job }

    init {
        lifecycleMonitor.onActivityEvent(::onLifecycleEvent)
    }

    override fun registerProvider(provider: AuthTokenProvider) {
        val transition = synchronized(completionBarrier) {
            val lifecycleTransition = synchronized(stateLock) {
                val cleanup = detachTokenStateLocked()
                state.profileGeneration++
                state.profileResetPending = false
                state.cachedToken = null
                state.provider = provider
                LifecycleTransition(cleanup, state.profileGeneration)
            }
            completeCleanup(lifecycleTransition.cleanup)
            lifecycleTransition
        }
        Registry.log.info("AuthTokenProvider registered")
        scope.safeLaunch {
            tryEagerFetch(RequestGuard(profileGeneration = transition.profileGeneration))
        }
    }

    override fun unregisterProvider() {
        val didUnregister = synchronized(completionBarrier) {
            val cleanup = synchronized(stateLock) state@{
                if (state.provider == null) return@state null
                state.provider = null
                state.profileGeneration++
                state.resetGeneration++
                val detached = detachTokenStateLocked()
                state.cachedToken = null
                state.profileResetPending = false
                detached
            }
            cleanup ?: return@synchronized false
            completeCleanup(cleanup)
            true
        }
        if (!didUnregister) return
        Registry.log.info("AuthTokenProvider unregistered")
    }

    override fun onTokenRefresh(observer: TokenRefreshObserver) {
        synchronized(stateLock) { state.refreshObservers.add(observer) }
    }

    override fun offTokenRefresh(observer: TokenRefreshObserver) {
        synchronized(stateLock) { state.refreshObservers.remove(observer) }
    }

    override fun invalidate(): Long = synchronized(completionBarrier) {
        synchronized(stateLock) {
            // Detach without cancelling so completion remains orderly. Existing waiters revalidate
            // the generation and retry; later callers cannot join the outgoing-profile fetch.
            state.inFlightFetch?.let(state.detachedFetches::add)
            state.inFlightFetch = null
            state.profileResetPending = true
            state.profileGeneration++
            state.resetGeneration++
            state.profileGeneration
        }
    }

    override suspend fun clearTokenState(expectedGeneration: Long) {
        val cleared = synchronized(completionBarrier) {
            val cleanup = synchronized(stateLock) state@{
                if (expectedGeneration >= 0L && state.profileGeneration != expectedGeneration) {
                    return@state null
                }
                val detached = detachTokenStateLocked()
                state.cachedToken = null
                state.profileGeneration++
                state.resetGeneration++
                state.profileResetPending = false
                detached
            }
            cleanup ?: return@synchronized false
            completeCleanup(cleanup)
            true
        }
        if (!cleared) {
            Registry.log.verbose("clearTokenState: skipped — provider re-registered since reset")
            return
        }
        Registry.log.info("Token state cleared")
    }

    override suspend fun currentToken(timeoutMs: Long): ValidatedToken =
        getOrFetchToken(timeoutMs = timeoutMs, allowCachedToken = true)

    private suspend fun tryEagerFetch(guard: RequestGuard) {
        try {
            getOrFetchToken(
                timeoutMs = AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS,
                allowCachedToken = true,
                guard = guard
            )
        } catch (_: StaleTriggerException) {
            // A newer lifecycle transition superseded this queued background request.
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Validation and timeout paths already log. Provider failures remain caller-owned.
        }
    }

    /**
     * Resolves cache hits and fetch deduplication through the serialized [state]. A shared fetch
     * reports a value outcome instead of cancelling its result deferred, so a provider replacement
     * can be distinguished from a provider that deliberately reports [CancellationException].
     */
    private suspend fun getOrFetchToken(
        timeoutMs: Long,
        allowCachedToken: Boolean,
        guard: RequestGuard? = null
    ): ValidatedToken {
        require(timeoutMs > 0L) { "timeoutMs must be positive, but was $timeoutMs" }

        val token = withTimeoutOrNull(timeoutMs) {
            while (true) {
                when (val request = tokenRequest(allowCachedToken, guard)) {
                    is TokenRequest.Cached -> return@withTimeoutOrNull request.token
                    is TokenRequest.Fetch -> when (val outcome = request.outcome.await()) {
                        is FetchOutcome.Success -> {
                            if (canReturnFetchResult(request.profileGeneration)) {
                                return@withTimeoutOrNull outcome.token
                            }
                            continue
                        }
                        is FetchOutcome.Failure -> throw outcome.error
                        FetchOutcome.Superseded -> continue
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("Token request loop terminated unexpectedly")
        }

        if (token != null) return token
        val error = AuthTokenException.TimedOut
        Registry.log.warning(requireNotNull(error.message), error)
        throw error
    }

    private fun tokenRequest(allowCachedToken: Boolean, guard: RequestGuard?): TokenRequest {
        var fetchToStart: InFlightFetch? = null
        val nowSeconds = Registry.clock.currentTimeMillis() / 1000L
        val request = synchronized(stateLock) {
            if (guard != null && !guard.matchesLocked()) throw StaleTriggerException()
            val provider = state.provider ?: throw AuthTokenException.NoProviderRegistered
            if (allowCachedToken) {
                usableCachedTokenLocked(nowSeconds)?.let {
                    return@synchronized TokenRequest.Cached(it)
                }
            }
            val inFlight = state.inFlightFetch ?: createFetchLocked(provider).also {
                state.inFlightFetch = it
                fetchToStart = it
            }
            TokenRequest.Fetch(inFlight.profileGeneration, inFlight.outcome)
        }
        // Start only after publishing the slot and releasing stateLock. This remains safe if a
        // concurrent lifecycle transition retires the lazy job before start() is reached.
        fetchToStart?.job?.start()
        return request
    }

    /**
     * Creates the shared fetch slot before starting its coroutine. A synchronous host callback can
     * therefore never complete before [state.inFlightFetch] contains the matching fetch identity.
     */
    private fun createFetchLocked(provider: AuthTokenProvider): InFlightFetch {
        val fetchId = ++state.nextFetchId
        val profileGeneration = state.profileGeneration
        val outcome = CompletableDeferred<FetchOutcome>()
        val job = scope.safeLaunch(start = CoroutineStart.LAZY) {
            val result = try {
                val jwt = invokeProvider(provider)
                FetchOutcome.Success(validateOrThrow(jwt))
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) return@safeLaunch
                FetchOutcome.Failure(e)
            } catch (e: Throwable) {
                FetchOutcome.Failure(e)
            }
            completeFetch(fetchId, profileGeneration, outcome, result)
        }
        return InFlightFetch(
            id = fetchId,
            profileGeneration = profileGeneration,
            outcome = outcome,
            job = job
        )
    }

    /**
     * Commits a provider result only when both the fetch identity and profile generation still
     * match. Provider replacement, unregister, and clear synchronously retire the slot. Invalidate
     * advances the generation so an original waiter discards its late result and retries; the stale
     * result cannot be cached, published, or returned.
     */
    private fun completeFetch(
        fetchId: Long,
        profileGeneration: Long,
        outcome: CompletableDeferred<FetchOutcome>,
        result: FetchOutcome
    ) {
        val tokenToNotify = synchronized(completionBarrier) {
            val scheduleTiming = (result as? FetchOutcome.Success)?.let {
                val nowMs = Registry.clock.currentTimeMillis()
                RefreshTiming(
                    nowMs = nowMs,
                    targetMs = computeRefreshTarget(it.token, nowMs)
                )
            }
            val refreshPlan = synchronized(stateLock) state@{
                val inFlight = state.inFlightFetch
                if (inFlight?.id != fetchId ||
                    inFlight.profileGeneration != profileGeneration
                ) {
                    state.detachedFetches.removeAll {
                        it.id == fetchId && it.outcome === outcome
                    }
                    return@state null
                }
                state.inFlightFetch = null
                if (state.profileGeneration != profileGeneration ||
                    state.profileResetPending ||
                    result !is FetchOutcome.Success
                ) {
                    return@state null
                }

                state.cachedToken = result.token
                prepareRefreshScheduleLocked(requireNotNull(scheduleTiming))
            }

            refreshPlan?.let(::installRefreshSchedule)
            // Completing outside stateLock prevents unconfined waiters from observing a partially
            // applied lifecycle transition or running application code under the global monitor.
            outcome.complete(result)

            val token = (result as? FetchOutcome.Success)?.token
            if (refreshPlan != null && token != null) {
                Registry.log.info(
                    "Auth token acquired " +
                        "(exp=${token.expiresAtEpochSeconds}, iat=${token.issuedAtEpochSeconds})"
                )
                token
            } else {
                null
            }
        }
        // Host callbacks run after the completion/lifecycle barrier is released. They remain
        // serialized by observerDispatchLock, which lifecycle APIs never acquire.
        tokenToNotify?.let { notifyRefreshObservers(it, profileGeneration) }
    }

    private fun canReturnFetchResult(profileGeneration: Long): Boolean =
        synchronized(completionBarrier) {
            synchronized(stateLock) { state.profileGeneration == profileGeneration }
        }

    private suspend fun invokeProvider(provider: AuthTokenProvider): String =
        suspendCancellableCoroutine { continuation ->
            val callback = object : AuthTokenProvider.Callback {
                override fun onSuccess(jwt: String) {
                    if (continuation.isActive) continuation.resume(jwt)
                }

                override fun onFailure(error: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
            provider.fetchToken(callback)
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

    private fun usableCachedTokenLocked(nowSeconds: Long): ValidatedToken? =
        state.cachedToken?.takeIf {
            isStillValid(it, nowSeconds) && !state.profileResetPending
        }

    private fun isStillValid(token: ValidatedToken, nowSeconds: Long): Boolean =
        nowSeconds < token.expiresAtEpochSeconds - JWTParser.DEFAULT_LEEWAY_SECONDS

    private fun detachTokenStateLocked(): Cleanup = Cleanup(
        fetches = buildList {
            state.inFlightFetch?.let(::add)
            addAll(state.detachedFetches)
        }.also {
            state.inFlightFetch = null
            state.detachedFetches.clear()
        },
        refreshJob = detachRefreshLocked(),
        connectivityJob = detachConnectivityWaitLocked()
    )

    private fun completeCleanup(cleanup: Cleanup) {
        cleanup.fetches.forEach { fetch ->
            fetch.outcome.complete(FetchOutcome.Superseded)
            fetch.job.cancel()
        }
        cleanup.refreshJob?.cancel()
        cleanup.connectivityJob?.cancel()
    }

    private fun detachRefreshLocked(): Clock.Cancellable? {
        val refreshJob = state.refreshJob
        state.refreshGeneration++
        state.refreshJob = null
        state.refreshAtWallClockMs = null
        state.refreshTimerFired = false
        return refreshJob
    }

    private fun prepareRefreshScheduleLocked(timing: RefreshTiming): RefreshSchedule {
        val generation = ++state.refreshGeneration
        val previousJob = state.refreshJob
        state.refreshJob = null
        state.refreshTimerFired = false
        state.refreshAtWallClockMs = timing.targetMs
        return RefreshSchedule(
            generation = generation,
            targetMs = timing.targetMs,
            delayMs = (timing.targetMs - timing.nowMs).coerceAtLeast(0L),
            previousJob = previousJob
        )
    }

    private fun installRefreshSchedule(schedule: RefreshSchedule) {
        schedule.previousJob?.cancel()
        val refreshJob = Registry.clock.schedule(schedule.delayMs) {
            onRefreshTimer(schedule.generation)
        }
        val installed = synchronized(stateLock) {
            if (state.refreshGeneration != schedule.generation ||
                state.refreshAtWallClockMs != schedule.targetMs
            ) {
                return@synchronized false
            }
            state.refreshJob = refreshJob
            true
        }
        if (!installed) {
            refreshJob.cancel()
            return
        }
        Registry.log.info(
            "Proactive token refresh scheduled " +
                "(target=${Registry.clock.isoTime(schedule.targetMs)})"
        )
    }

    private fun RequestGuard.matchesLocked(): Boolean =
        !state.profileResetPending &&
            (profileGeneration == null || state.profileGeneration == profileGeneration) &&
            (resetGeneration == null || state.resetGeneration == resetGeneration) &&
            (refreshGeneration == null || state.refreshGeneration == refreshGeneration) &&
            (
                connectivityGeneration == null ||
                    state.connectivityWait?.generation == connectivityGeneration
                )

    private fun onRefreshTimer(generation: Long) {
        val guard = synchronized(stateLock) {
            if (state.refreshGeneration != generation || state.profileResetPending) {
                return@synchronized null
            }
            state.refreshTimerFired = true
            RequestGuard(
                profileGeneration = state.profileGeneration,
                resetGeneration = state.resetGeneration,
                refreshGeneration = generation
            )
        }
        if (guard != null) {
            scope.safeLaunch {
                performScheduledRefresh(guard = guard, timerGeneration = generation)
            }
        }
    }

    private suspend fun performScheduledRefresh(
        guard: RequestGuard,
        timerGeneration: Long? = null,
        allowImmediateConnectivityRetry: Boolean = true
    ) {
        val (profileGenerationAtStart, resetGenerationAtStart) = synchronized(stateLock) {
            if (!guard.matchesLocked() || state.provider == null) return
            state.profileGeneration to state.resetGeneration
        }
        Registry.log.info("Proactive token refresh fired")
        try {
            getOrFetchToken(
                timeoutMs = AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS,
                allowCachedToken = false,
                guard = guard
            )
            Registry.log.info("Proactive token refresh succeeded")
        } catch (_: StaleTriggerException) {
            // Teardown or provider replacement won the race before fetch reservation.
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (timerGeneration != null) clearFiredFlagForFailedRefresh(timerGeneration)
            Registry.log.warning("Proactive token refresh failed: ${e.javaClass.simpleName}", e)
            if (isNetworkException(e)) {
                armConnectivityWaitJob(
                    expectedProfileGeneration = profileGenerationAtStart,
                    expectedResetGeneration = resetGenerationAtStart,
                    resumeImmediatelyIfConnected = allowImmediateConnectivityRetry
                )
            }
        }
    }

    private fun clearFiredFlagForFailedRefresh(timerGeneration: Long) {
        synchronized(stateLock) {
            if (state.refreshGeneration == timerGeneration) {
                state.refreshTimerFired = false
            }
        }
    }

    private fun notifyRefreshObservers(token: ValidatedToken, profileGeneration: Long) {
        synchronized(observerDispatchLock) {
            val observers = synchronized(stateLock) {
                if (!canDeliverTokenLocked(token, profileGeneration)) return
                state.refreshObservers.toList()
            }
            observers.forEach { observer ->
                val canDeliver = synchronized(stateLock) {
                    canDeliverTokenLocked(token, profileGeneration)
                }
                if (!canDeliver) return
                try {
                    observer(token.rawToken)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Registry.log.warning(
                        "TokenRefreshObserver threw ${e.javaClass.simpleName} — skipping",
                        e
                    )
                }
            }
        }
    }

    private fun canDeliverTokenLocked(token: ValidatedToken, profileGeneration: Long): Boolean =
        state.profileGeneration == profileGeneration &&
            !state.profileResetPending &&
            state.cachedToken?.rawToken == token.rawToken

    private fun isNetworkException(e: Exception): Boolean =
        e is UnknownHostException ||
            e is SocketTimeoutException ||
            e is ConnectException ||
            e is IOException

    private fun armConnectivityWaitJob(
        expectedProfileGeneration: Long,
        expectedResetGeneration: Long,
        resumeImmediatelyIfConnected: Boolean = true
    ) {
        var previousJob: Job? = null
        val waitJob = synchronized(stateLock) {
            if (state.profileGeneration != expectedProfileGeneration ||
                state.resetGeneration != expectedResetGeneration ||
                state.provider == null ||
                state.profileResetPending
            ) {
                return@synchronized null
            }
            previousJob = state.connectivityWait?.job
            val generation = ++state.connectivityWaitGeneration
            val job = scope.safeLaunch(start = CoroutineStart.LAZY) {
                try {
                    awaitConnectivity(resumeImmediatelyIfConnected)
                    currentCoroutineContext().ensureActive()
                    Registry.log.info(
                        "AuthTokenManager: connectivity restored — retrying proactive refresh"
                    )
                    performScheduledRefresh(
                        guard = RequestGuard(
                            profileGeneration = expectedProfileGeneration,
                            resetGeneration = expectedResetGeneration,
                            connectivityGeneration = generation
                        ),
                        allowImmediateConnectivityRetry = false
                    )
                } finally {
                    synchronized(stateLock) {
                        if (state.connectivityWait?.generation == generation) {
                            state.connectivityWait = null
                        }
                    }
                }
            }
            state.connectivityWait = ConnectivityWait(generation, job)
            job
        }
        waitJob ?: return
        previousJob?.cancel()
        Registry.log.info(
            "AuthTokenManager: network failure — waiting for connectivity to retry refresh"
        )
        // Register the generation-tagged slot before starting work, but never invoke the network
        // monitor while holding stateLock (including with an unconfined test dispatcher).
        waitJob.start()
    }

    private suspend fun awaitConnectivity(resumeImmediatelyIfConnected: Boolean) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val resumed = AtomicBoolean(false)
            val observerRef = arrayOfNulls<NetworkObserver>(1)
            val observer: NetworkObserver = { isConnected ->
                if (isConnected && resumed.compareAndSet(false, true)) {
                    observerRef[0]?.let { Registry.networkMonitor.offNetworkChange(it) }
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            observerRef[0] = observer
            Registry.networkMonitor.onNetworkChange(observer)
            continuation.invokeOnCancellation {
                Registry.networkMonitor.offNetworkChange(observer)
            }
            if (resumeImmediatelyIfConnected &&
                Registry.networkMonitor.isNetworkConnected() &&
                resumed.compareAndSet(false, true)
            ) {
                Registry.networkMonitor.offNetworkChange(observer)
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    private fun detachConnectivityWaitLocked(): Job? {
        val job = state.connectivityWait?.job
        state.connectivityWaitGeneration++
        state.connectivityWait = null
        return job
    }

    private fun onLifecycleEvent(event: ActivityEvent) {
        event.takeIf<ActivityEvent.FirstStarted>() ?: return
        scope.safeLaunch { handleForegroundTransition() }
    }

    private fun handleForegroundTransition() {
        val nowMs = Registry.clock.currentTimeMillis()
        var refreshToCancel: Clock.Cancellable? = null
        val action = synchronized(stateLock) {
            val cached = state.cachedToken
            val targetMs = state.refreshAtWallClockMs
            when {
                state.profileResetPending -> ForegroundAction.None
                cached != null && !isStillValid(cached, nowMs / 1000L) -> {
                    state.cachedToken = null
                    refreshToCancel = detachRefreshLocked()
                    ForegroundAction.EagerFetch(
                        RequestGuard(profileGeneration = state.profileGeneration)
                    )
                }
                targetMs != null && nowMs >= targetMs && !state.refreshTimerFired -> {
                    refreshToCancel = detachRefreshLocked()
                    ForegroundAction.ScheduledRefresh(
                        RequestGuard(profileGeneration = state.profileGeneration)
                    )
                }
                else -> ForegroundAction.None
            }
        }
        refreshToCancel?.cancel()

        when (action) {
            is ForegroundAction.EagerFetch -> {
                Registry.log.info(
                    "AuthTokenManager: foreground transition (case=expired-cached-token)"
                )
                scope.safeLaunch { tryEagerFetch(action.guard) }
            }
            is ForegroundAction.ScheduledRefresh -> {
                Registry.log.info(
                    "AuthTokenManager: foreground transition (case=missed-refresh)"
                )
                scope.safeLaunch { performScheduledRefresh(guard = action.guard) }
            }
            ForegroundAction.None -> Registry.log.info(
                "AuthTokenManager: foreground transition (case=still-valid)"
            )
        }
    }

    private class State {
        var provider: AuthTokenProvider? = null
        var cachedToken: ValidatedToken? = null
        var inFlightFetch: InFlightFetch? = null
        val detachedFetches = mutableListOf<InFlightFetch>()
        var nextFetchId: Long = 0L
        var refreshJob: Clock.Cancellable? = null
        var refreshAtWallClockMs: Long? = null
        var refreshTimerFired: Boolean = false
        var refreshGeneration: Long = 0L
        var profileGeneration: Long = 0L
        var resetGeneration: Long = 0L
        var profileResetPending: Boolean = false
        val refreshObservers = mutableListOf<TokenRefreshObserver>()
        var connectivityWait: ConnectivityWait? = null
        var connectivityWaitGeneration: Long = 0L
    }

    private data class InFlightFetch(
        val id: Long,
        val profileGeneration: Long,
        val outcome: CompletableDeferred<FetchOutcome>,
        val job: Job
    )

    private data class ConnectivityWait(
        val generation: Long,
        val job: Job
    )

    private data class Cleanup(
        val fetches: List<InFlightFetch>,
        val refreshJob: Clock.Cancellable?,
        val connectivityJob: Job?
    )

    private data class LifecycleTransition(
        val cleanup: Cleanup,
        val profileGeneration: Long
    )

    private data class RefreshSchedule(
        val generation: Long,
        val targetMs: Long,
        val delayMs: Long,
        val previousJob: Clock.Cancellable?
    )

    private data class RefreshTiming(
        val nowMs: Long,
        val targetMs: Long
    )

    private data class RequestGuard(
        val profileGeneration: Long? = null,
        val resetGeneration: Long? = null,
        val refreshGeneration: Long? = null,
        val connectivityGeneration: Long? = null
    )

    private class StaleTriggerException : Exception()

    private sealed interface FetchOutcome {
        data class Success(val token: ValidatedToken) : FetchOutcome
        data class Failure(val error: Throwable) : FetchOutcome
        data object Superseded : FetchOutcome
    }

    private sealed interface TokenRequest {
        data class Cached(val token: ValidatedToken) : TokenRequest
        data class Fetch(
            val profileGeneration: Long,
            val outcome: CompletableDeferred<FetchOutcome>
        ) : TokenRequest
    }

    private sealed interface ForegroundAction {
        data class EagerFetch(val guard: RequestGuard) : ForegroundAction
        data class ScheduledRefresh(val guard: RequestGuard) : ForegroundAction
        data object None : ForegroundAction
    }

    companion object {
        /**
         * Computes the absolute wall-clock target (epoch ms) for the next proactive refresh.
         *
         * Ideal: iat + 0.9 * (exp - iat). Clamped to [now + 5s, exp - leeway].
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

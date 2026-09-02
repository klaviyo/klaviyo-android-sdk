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
    private val state = State()

    internal val connectivityWaitJob: Job?
        get() = synchronized(stateLock) { state.connectivityWait?.job }

    init {
        lifecycleMonitor.onActivityEvent(::onLifecycleEvent)
    }

    override fun registerProvider(provider: AuthTokenProvider) {
        synchronized(stateLock) {
            cancelInFlightFetchLocked()
            cancelRefreshLocked()
            cancelConnectivityWaitLocked()
            state.profileGeneration++
            state.profileResetPending = false
            state.cachedToken = null
            state.provider = provider
        }
        Registry.log.info("AuthTokenProvider registered")
        scope.safeLaunch { tryEagerFetch() }
    }

    override fun unregisterProvider() {
        val didUnregister = synchronized(stateLock) {
            if (state.provider == null) return@synchronized false
            state.provider = null
            state.profileGeneration++
            state.resetGeneration++
            cancelInFlightFetchLocked()
            cancelRefreshLocked()
            cancelConnectivityWaitLocked()
            state.cachedToken = null
            state.profileResetPending = false
            true
        }
        if (didUnregister) Registry.log.info("AuthTokenProvider unregistered")
    }

    override fun onTokenRefresh(observer: TokenRefreshObserver) {
        synchronized(stateLock) { state.refreshObservers.add(observer) }
    }

    override fun offTokenRefresh(observer: TokenRefreshObserver) {
        synchronized(stateLock) { state.refreshObservers.remove(observer) }
    }

    override fun invalidate(): Long = synchronized(stateLock) {
        state.profileResetPending = true
        state.profileGeneration++
        state.resetGeneration++
        state.profileGeneration
    }

    override suspend fun clearTokenState(expectedGeneration: Long) {
        val cleared = synchronized(stateLock) {
            if (expectedGeneration >= 0L && state.profileGeneration != expectedGeneration) {
                Registry.log.verbose(
                    "clearTokenState: skipped — provider re-registered since reset"
                )
                return@synchronized false
            }
            cancelInFlightFetchLocked()
            cancelRefreshLocked()
            cancelConnectivityWaitLocked()
            state.cachedToken = null
            state.profileGeneration++
            state.resetGeneration++
            state.profileResetPending = false
            true
        }
        if (cleared) Registry.log.info("Token state cleared")
    }

    override suspend fun currentToken(timeoutMs: Long): ValidatedToken =
        getOrFetchToken(timeoutMs = timeoutMs, allowCachedToken = true)

    private suspend fun tryEagerFetch() {
        try {
            currentToken(AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS)
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
        allowCachedToken: Boolean
    ): ValidatedToken {
        require(timeoutMs > 0L) { "timeoutMs must be positive, but was $timeoutMs" }

        val token = withTimeoutOrNull(timeoutMs) {
            while (true) {
                when (val request = tokenRequest(allowCachedToken)) {
                    is TokenRequest.Cached -> return@withTimeoutOrNull request.token
                    is TokenRequest.Fetch -> when (val outcome = request.outcome.await()) {
                        is FetchOutcome.Success -> return@withTimeoutOrNull outcome.token
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

    private fun tokenRequest(allowCachedToken: Boolean): TokenRequest {
        var fetchToStart: InFlightFetch? = null
        val request = synchronized(stateLock) {
            val provider = state.provider ?: throw AuthTokenException.NoProviderRegistered
            if (allowCachedToken) {
                usableCachedTokenLocked()?.let { return@synchronized TokenRequest.Cached(it) }
            }
            val inFlight = state.inFlightFetch ?: createFetchLocked(provider).also {
                state.inFlightFetch = it
                fetchToStart = it
            }
            TokenRequest.Fetch(inFlight.outcome)
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
            completeFetch(fetchId, profileGeneration, result)
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
     * advances the generation so its late result may satisfy the original waiter but cannot cache
     * or publish a stale JWT.
     */
    private fun completeFetch(
        fetchId: Long,
        profileGeneration: Long,
        result: FetchOutcome
    ) {
        synchronized(stateLock) {
            val inFlight = state.inFlightFetch
            if (inFlight?.id != fetchId ||
                inFlight.profileGeneration != profileGeneration
            ) {
                return
            }

            if (state.profileGeneration != profileGeneration) {
                state.inFlightFetch = null
                // invalidate() advances the profile generation without cancelling the provider
                // callback. Its direct waiter still receives that outcome, but the token is never
                // committed or broadcast. Provider replacement and full clear retire the slot
                // synchronously, so their late completions never reach this branch.
                if (state.profileResetPending) {
                    inFlight.outcome.complete(result)
                } else {
                    inFlight.outcome.complete(FetchOutcome.Superseded)
                }
                return
            }

            state.inFlightFetch = null
            when (result) {
                is FetchOutcome.Failure -> inFlight.outcome.complete(result)
                FetchOutcome.Superseded -> inFlight.outcome.complete(FetchOutcome.Superseded)
                is FetchOutcome.Success -> {
                    // Fetches started while reset is pending may satisfy their direct caller, but
                    // cannot become shared state or publish to observers.
                    if (state.profileResetPending) {
                        inFlight.outcome.complete(result)
                        return
                    }

                    state.cachedToken = result.token
                    scheduleRefreshLocked(result.token)
                    inFlight.outcome.complete(result)
                    Registry.log.info(
                        "Auth token acquired " +
                            "(exp=${result.token.expiresAtEpochSeconds}, " +
                            "iat=${result.token.issuedAtEpochSeconds})"
                    )
                    notifyRefreshObserversLocked(result.token, profileGeneration)
                }
            }
        }
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

    private fun usableCachedTokenLocked(): ValidatedToken? = state.cachedToken?.takeIf {
        isStillValid(it) && !state.profileResetPending
    }

    private fun isStillValid(token: ValidatedToken): Boolean {
        val now = Registry.clock.currentTimeMillis() / 1000L
        return now < token.expiresAtEpochSeconds - JWTParser.DEFAULT_LEEWAY_SECONDS
    }

    private fun cancelInFlightFetchLocked() {
        val inFlight = state.inFlightFetch ?: return
        state.inFlightFetch = null
        inFlight.outcome.complete(FetchOutcome.Superseded)
        inFlight.job.cancel()
    }

    private fun cancelRefreshLocked() {
        state.refreshGeneration++
        state.refreshJob?.cancel()
        state.refreshJob = null
        state.refreshAtWallClockMs = null
        state.refreshTimerFired = false
    }

    private fun scheduleRefreshLocked(token: ValidatedToken) {
        val nowMs = Registry.clock.currentTimeMillis()
        val targetMs = computeRefreshTarget(token, nowMs)
        val generation = ++state.refreshGeneration
        state.refreshJob?.cancel()
        state.refreshTimerFired = false
        state.refreshAtWallClockMs = targetMs
        state.refreshJob = Registry.clock.schedule((targetMs - nowMs).coerceAtLeast(0L)) {
            onRefreshTimer(generation)
        }
        Registry.log.info(
            "Proactive token refresh scheduled (target=${Registry.clock.isoTime(targetMs)})"
        )
    }

    private fun onRefreshTimer(generation: Long) {
        val shouldRefresh = synchronized(stateLock) {
            if (state.refreshGeneration != generation) return@synchronized false
            state.refreshTimerFired = true
            true
        }
        if (shouldRefresh) {
            scope.safeLaunch { performScheduledRefresh(timerGeneration = generation) }
        }
    }

    private suspend fun performScheduledRefresh(
        timerGeneration: Long? = null,
        allowImmediateConnectivityRetry: Boolean = true
    ) {
        val resetGenerationAtStart = synchronized(stateLock) {
            if (state.provider == null) return
            state.resetGeneration
        }
        Registry.log.info("Proactive token refresh fired")
        try {
            getOrFetchToken(
                timeoutMs = AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS,
                allowCachedToken = false
            )
            Registry.log.info("Proactive token refresh succeeded")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (timerGeneration != null) clearFiredFlagForFailedRefresh(timerGeneration)
            Registry.log.warning("Proactive token refresh failed: ${e.javaClass.simpleName}", e)
            val shouldWait = synchronized(stateLock) {
                state.resetGeneration == resetGenerationAtStart &&
                    isNetworkException(e) &&
                    state.provider != null &&
                    !state.profileResetPending
            }
            if (shouldWait) {
                armConnectivityWaitJob(
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

    /**
     * Observer delivery is part of the same serialized transition as the cache commit. Because a
     * JVM monitor is reentrant, an observer may call a synchronous manager API without deadlocking;
     * the generation guard is re-checked before each remaining callback in case it does so.
     */
    private fun notifyRefreshObserversLocked(token: ValidatedToken, profileGeneration: Long) {
        val observers = state.refreshObservers.toList()
        observers.forEach { observer ->
            if (state.profileGeneration != profileGeneration ||
                state.profileResetPending ||
                state.cachedToken?.rawToken != token.rawToken
            ) {
                return
            }
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

    private fun isNetworkException(e: Exception): Boolean =
        e is UnknownHostException ||
            e is SocketTimeoutException ||
            e is ConnectException ||
            e is IOException

    private fun armConnectivityWaitJob(resumeImmediatelyIfConnected: Boolean = true) {
        Registry.log.info(
            "AuthTokenManager: network failure — waiting for connectivity to retry refresh"
        )
        val waitJob = synchronized(stateLock) {
            cancelConnectivityWaitLocked()
            val generation = state.connectivityWaitGeneration
            val job = scope.safeLaunch(start = CoroutineStart.LAZY) {
                try {
                    awaitConnectivity(resumeImmediatelyIfConnected)
                    Registry.log.info(
                        "AuthTokenManager: connectivity restored — retrying proactive refresh"
                    )
                    currentCoroutineContext().ensureActive()
                    val isCurrent = synchronized(stateLock) {
                        state.connectivityWait?.generation == generation
                    }
                    if (isCurrent) {
                        performScheduledRefresh(allowImmediateConnectivityRetry = false)
                    }
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

    private fun cancelConnectivityWaitLocked() {
        val job = state.connectivityWait?.job
        state.connectivityWaitGeneration++
        state.connectivityWait = null
        job?.cancel()
    }

    private fun onLifecycleEvent(event: ActivityEvent) {
        event.takeIf<ActivityEvent.FirstStarted>() ?: return
        scope.safeLaunch { handleForegroundTransition() }
    }

    private fun handleForegroundTransition() {
        val nowMs = Registry.clock.currentTimeMillis()
        val action = synchronized(stateLock) {
            val cached = state.cachedToken
            val targetMs = state.refreshAtWallClockMs
            when {
                cached != null && !isStillValid(cached) -> {
                    state.cachedToken = null
                    cancelRefreshLocked()
                    ForegroundAction.EagerFetch
                }
                targetMs != null && nowMs >= targetMs && !state.refreshTimerFired -> {
                    cancelRefreshLocked()
                    ForegroundAction.ScheduledRefresh
                }
                else -> ForegroundAction.None
            }
        }

        when (action) {
            ForegroundAction.EagerFetch -> {
                Registry.log.info(
                    "AuthTokenManager: foreground transition (case=expired-cached-token)"
                )
                scope.safeLaunch { tryEagerFetch() }
            }
            ForegroundAction.ScheduledRefresh -> {
                Registry.log.info(
                    "AuthTokenManager: foreground transition (case=missed-refresh)"
                )
                scope.safeLaunch { performScheduledRefresh() }
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

    private sealed interface FetchOutcome {
        data class Success(val token: ValidatedToken) : FetchOutcome
        data class Failure(val error: Throwable) : FetchOutcome
        data object Superseded : FetchOutcome
    }

    private sealed interface TokenRequest {
        data class Cached(val token: ValidatedToken) : TokenRequest
        data class Fetch(val outcome: CompletableDeferred<FetchOutcome>) : TokenRequest
    }

    private enum class ForegroundAction {
        EagerFetch,
        ScheduledRefresh,
        None
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

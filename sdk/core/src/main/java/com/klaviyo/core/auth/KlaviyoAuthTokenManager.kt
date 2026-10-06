package com.klaviyo.core.auth

import androidx.annotation.VisibleForTesting
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.LifecycleMonitor
import com.klaviyo.core.networking.NetworkObserver
import com.klaviyo.core.safeLaunch
import com.klaviyo.core.utils.takeIf
import java.net.SocketException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Serializes token state transitions through one command consumer. The generation is advanced by
 * synchronous lifecycle calls, then paired with their queued commands. Only the consumer writes
 * [tokenSnapshot]; synchronous callers compare it with the latest generation.
 *
 * Work called by the consumer, including clock scheduling and logging, must not block waiting for
 * a manager command. Observer callbacks run on a separate serial delivery coroutine.
 */
internal class KlaviyoAuthTokenManager(
    private val lifecycleMonitor: LifecycleMonitor = Registry.lifecycleMonitor
) : AuthTokenManager {

    internal val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Registry.dispatcher)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val commands = Channel<Command>(Channel.UNLIMITED)
    private val deliveries = Channel<Delivery>(Channel.UNLIMITED)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val generation = AtomicLong()

    private val profileGeneration = AtomicLong()

    @Volatile private var tokenSnapshot = TokenSnapshot(-1L, null)
    private val state = State()

    init {
        scope.safeLaunch {
            try {
                for (command in commands) {
                    runCatching { handle(command) }.onFailure { error ->
                        failReply(command, error)
                        failWaiters(error)
                        retireWork()
                        cacheToken(null)
                        Registry.log.error("Auth token command failed", error)
                    }
                }
            } finally {
                retireWork()
                commands.close()
                deliveries.close()
                val failure = AuthTokenException.ManagerStopped
                failWaiters(failure)
                while (true) {
                    val pending = commands.tryReceive().getOrNull() ?: break
                    failReply(pending, failure)
                }
            }
        }
        scope.safeLaunch {
            for (delivery in deliveries) {
                try {
                    deliver(delivery)
                } catch (_: CancellationException) {
                    currentCoroutineContext().ensureActive()
                }
            }
        }
        lifecycleMonitor.onActivityEvent(::onLifecycleEvent)
    }

    override fun registerProvider(provider: AuthTokenProvider) {
        post(Command.Register(generation.incrementAndGet(), provider))
    }

    override fun unregisterProvider() {
        post(Command.Unregister(generation.incrementAndGet()))
    }

    override fun onTokenRefresh(observer: TokenRefreshObserver) {
        post(Command.Observe(observer))
    }

    override fun offTokenRefresh(observer: TokenRefreshObserver) {
        post(Command.Unobserve(observer))
    }

    override fun setIdentified(identified: Boolean) {
        post(Command.Identity(identified))
    }

    override fun profileGeneration(): Long = profileGeneration.get()

    override fun invalidate(): Long = generation.incrementAndGet().also {
        profileGeneration.incrementAndGet()
        post(Command.Invalidate(it))
    }

    override suspend fun clearTokenState(expectedGeneration: Long) {
        val reply = CompletableDeferred<Unit>()
        post(Command.Clear(expectedGeneration, reply))
        reply.await()
    }

    override suspend fun currentToken(timeoutMs: Long): ValidatedToken =
        requestToken(timeoutMs)

    override fun refreshRejectedToken() {
        post(Command.RefreshRejected)
    }

    override fun isCurrentToken(rawToken: String): Boolean {
        val snapshot = tokenSnapshot
        return snapshot.generation == generation.get() && snapshot.token?.rawToken == rawToken
    }

    internal suspend fun connectivityWaitJob(): Job? {
        val reply = CompletableDeferred<Job?>()
        post(Command.ConnectivityJob(reply))
        return reply.await()
    }

    private suspend fun requestToken(
        timeoutMs: Long,
        forceRefresh: Boolean = false,
        refreshId: Long? = null
    ): ValidatedToken {
        require(timeoutMs > 0L) { "timeoutMs must be positive, but was $timeoutMs" }
        val reply = CompletableDeferred<ValidatedToken>()
        try {
            return withTimeoutOrNull(timeoutMs) {
                post(Command.Token(reply, forceRefresh, refreshId))
                reply.await()
            } ?: run {
                Registry.log.warning(
                    requireNotNull(AuthTokenException.TimedOut.message),
                    AuthTokenException.TimedOut
                )
                throw AuthTokenException.TimedOut
            }
        } finally {
            commands.trySend(Command.CallerDone(reply))
        }
    }

    private fun post(command: Command) {
        if (scope.coroutineContext[Job]?.isActive != true || commands.trySend(command).isFailure) {
            Registry.log.warning("Auth token manager is closed", AuthTokenException.ManagerStopped)
            failReply(command, AuthTokenException.ManagerStopped)
        }
    }

    private fun failReply(command: Command, error: Throwable) {
        when (command) {
            is Command.Token -> command.reply.completeExceptionally(error)
            is Command.Clear -> command.reply.completeExceptionally(error)
            is Command.ConnectivityJob -> command.reply.completeExceptionally(error)
            is Command.CanDeliver -> command.reply.complete(false)
            else -> Unit
        }
    }

    private fun postFromWorker(command: Command): Boolean {
        val sent = commands.trySend(command)
        if (sent.isFailure && scope.coroutineContext[Job]?.isActive == true) {
            val error = IllegalStateException("Auth token mailbox closed")
            Registry.log.error("Auth token command could not be delivered", error)
        }
        return sent.isSuccess
    }

    private fun handle(command: Command) {
        when (command) {
            is Command.Register -> {
                if (command.generation < state.generation) {
                    Registry.log.verbose("Dropping stale provider registration")
                    return
                }
                retireWork()
                state.generation = command.generation
                state.provider = command.provider
                cacheToken(null)
                state.resetPending = false
                state.warmUpPending = true
                Registry.log.info("AuthTokenProvider registered")
                startFetch()
            }
            is Command.Unregister -> {
                if (command.generation < state.generation) {
                    Registry.log.verbose("Dropping stale provider unregistration")
                    return
                }
                val hadProvider = state.provider != null
                retireWork()
                state.generation = command.generation
                state.provider = null
                cacheToken(null)
                state.resetPending = false
                state.warmUpPending = false
                failWaiters(AuthTokenException.NoProviderRegistered)
                if (hadProvider) Registry.log.info("AuthTokenProvider unregistered")
            }
            is Command.Invalidate -> {
                if (command.generation < state.generation) {
                    Registry.log.verbose("Dropping stale profile invalidation")
                    return
                }
                retireWork()
                state.generation = command.generation
                state.resetId = maxOf(state.resetId, command.generation)
                cacheToken(null)
                state.resetPending = true
            }
            is Command.Clear -> {
                val shouldClear = command.expectedGeneration < 0L ||
                    state.resetPending &&
                    command.expectedGeneration == state.resetId &&
                    command.expectedGeneration == generation.get()
                if (shouldClear) {
                    retireWork()
                    cacheToken(null)
                    state.resetPending = false
                    if (state.warmUpPending || state.waiters.isNotEmpty()) startFetch()
                    Registry.log.info("Token state cleared")
                } else {
                    Registry.log.verbose("Dropping stale token clear")
                }
                command.reply.complete(Unit)
            }
            is Command.Token -> {
                if (command.refreshId != null &&
                    (command.refreshId != state.refreshId || state.generation != generation.get())
                ) {
                    command.reply.completeExceptionally(StaleRefreshException())
                } else if (state.provider == null) {
                    command.reply.completeExceptionally(AuthTokenException.NoProviderRegistered)
                } else if (!state.identified) {
                    command.reply.completeExceptionally(AuthTokenException.NotIdentified)
                } else if (state.resetPending || state.generation != generation.get()) {
                    addWaiter(command)
                } else {
                    val cached = state.cachedToken?.takeIf { isStillValid(it) }
                    if (cached != null && !command.forceRefresh) {
                        command.reply.complete(cached)
                    } else {
                        addWaiter(command)
                        startFetch()
                    }
                }
            }
            Command.RefreshRejected -> onTokenRejected()
            is Command.Identity -> onIdentityChange(command.identified)
            is Command.CallerDone -> state.waiters.removeAll { it.reply === command.reply }
            is Command.FetchDone -> onFetchDone(command)
            is Command.TimerFired -> {
                if (command.id == state.refreshId && !state.resetPending) {
                    Registry.log.info(PROACTIVE_REFRESH_FIRED)
                    launchRefresh(command.id, true)
                }
            }
            is Command.RefreshFailed -> {
                if (command.id == state.refreshId && !state.resetPending) {
                    state.refreshInFlight = false
                    Registry.log.warning(
                        "Proactive token refresh failed: ${command.error.javaClass.simpleName}",
                        command.error
                    )
                    val fetchFailedOffline = state.refreshFetchFailedOffline
                    state.refreshFetchFailedOffline = false
                    if (state.provider == null) return
                    if (isConnectivityError(command.error)) {
                        armConnectivityWait(command.allowImmediateRetry)
                    } else if (fetchFailedOffline && state.connectivityJob == null) {
                        armConnectivityWait(false)
                    }
                }
            }
            is Command.Connected -> {
                if (command.id == state.connectivityId && !state.resetPending) {
                    state.connectivityJob = null
                    if (!state.refreshInFlight) {
                        Registry.log.info(
                            "AuthTokenManager: connectivity restored — retrying token fetch"
                        )
                        launchRefresh(state.refreshId, false)
                    }
                }
            }
            is Command.CanDeliver -> {
                val canDeliver = command.id == state.deliveryId && !state.resetPending &&
                    state.generation == generation.get()
                command.reply.complete(canDeliver)
            }
            Command.Foreground -> onForeground()
            is Command.Observe -> state.observers.add(command.observer)
            is Command.Unobserve -> state.observers.remove(command.observer)
            is Command.ConnectivityJob -> command.reply.complete(state.connectivityJob)
        }
    }

    private fun addWaiter(command: Command.Token) {
        state.waiters.add(Waiter(command.reply, command.refreshId))
    }

    private fun onTokenRejected() {
        if (!state.identified || state.provider == null || state.resetPending ||
            state.generation != generation.get()
        ) {
            Registry.log.debug("Rejected auth token refresh skipped")
            return
        }
        cacheToken(null)
        state.deliveryId++
        if (!state.refreshInFlight) retireRefresh()
        Registry.log.info("Auth token rejected; fetching a replacement")
        startFetch()
    }

    private fun onIdentityChange(identified: Boolean) {
        if (identified == state.identified) return
        state.identified = identified
        if (identified) {
            Registry.log.verbose("Profile identified")
            if (state.warmUpPending) startFetch()
        } else {
            retireWork()
            cacheToken(null)
            failWaiters(AuthTokenException.NotIdentified)
            Registry.log.verbose(
                "Profile not identified — auth token requests resolve without a token"
            )
        }
    }

    private fun startFetch() {
        if (!state.identified || state.fetchJob != null || state.resetPending ||
            state.generation != generation.get()
        ) {
            return
        }
        val provider = state.provider ?: return
        val id = ++state.fetchId
        state.fetchAllowsImmediateRetry = state.waiters.none { it.refreshId != null }
        state.refreshFetchFailedOffline = false
        state.fetchJob = scope.safeLaunch {
            val result = runCatching {
                val jwt = invokeProvider(provider)
                validateOrThrow(jwt)
            }
            postFromWorker(Command.FetchDone(id, result))
        }
    }

    private fun onFetchDone(command: Command.FetchDone) {
        if (command.id != state.fetchId || state.generation != generation.get()) {
            Registry.log.verbose("Dropping stale token fetch result")
            return
        }
        state.fetchJob = null
        state.warmUpPending = false
        val refreshWaiting = state.waiters.any { it.refreshId != null }
        command.result.fold(
            onSuccess = { token ->
                cacheToken(token)
                state.connectivityId++
                state.connectivityJob?.cancel()
                state.connectivityJob = null
                scheduleRefresh(token)
                completeWaiters(token)
                val deliveryId = ++state.deliveryId
                Registry.log.info(
                    "Auth token acquired (exp=${token.expiresAtEpochSeconds}, iat=${token.issuedAtEpochSeconds})"
                )
                val observers = state.observers.toList()
                deliveries.trySend(Delivery(deliveryId, state.generation, token, observers))
            },
            onFailure = { error ->
                if (isConnectivityError(error)) {
                    if (refreshWaiting) {
                        state.refreshFetchFailedOffline = true
                    } else if (state.connectivityJob == null) {
                        armConnectivityWait(state.fetchAllowsImmediateRetry)
                    }
                }
                failWaiters(error)
            }
        )
    }

    private suspend fun deliver(delivery: Delivery) {
        for (observer in delivery.observers) {
            val permission = CompletableDeferred<Boolean>()
            if (!postFromWorker(Command.CanDeliver(delivery.id, permission))) return
            if (!permission.await() || generation.get() != delivery.generation) return
            try {
                observer(delivery.token.rawToken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Registry.log.warning("TokenRefreshObserver threw ${e.javaClass.simpleName}", e)
            }
        }
    }

    private fun completeWaiters(token: ValidatedToken) {
        state.waiters.forEach { it.reply.complete(token) }
        state.waiters.clear()
    }

    private fun failWaiters(error: Throwable) {
        state.waiters.forEach { it.reply.completeExceptionally(error) }
        state.waiters.clear()
    }

    /** Set the cached token and publish it with the current generation to [tokenSnapshot]. */
    private fun cacheToken(token: ValidatedToken?) {
        state.cachedToken = token
        tokenSnapshot = TokenSnapshot(state.generation, token)
    }

    private fun retireWork() {
        state.waiters.removeAll { waiter ->
            if (waiter.refreshId == null) {
                false
            } else {
                waiter.reply.completeExceptionally(StaleRefreshException())
                true
            }
        }
        state.fetchId++
        state.deliveryId++
        state.fetchJob?.cancel()
        state.fetchJob = null
        state.refreshFetchFailedOffline = false
        retireRefresh()
        state.connectivityId++
        state.connectivityJob?.cancel()
        state.connectivityJob = null
    }

    private fun retireRefresh() {
        state.refreshId++
        state.refreshTimer?.cancel()
        state.refreshTimer = null
        state.refreshAt = null
        state.refreshInFlight = false
    }

    private fun scheduleRefresh(token: ValidatedToken) {
        val now = Registry.clock.currentTimeMillis()
        val target = computeRefreshTarget(token, now)
        val id = ++state.refreshId
        state.refreshTimer?.cancel()
        state.refreshAt = target
        state.refreshInFlight = false
        state.refreshTimer = Registry.clock.schedule((target - now).coerceAtLeast(0)) {
            postFromWorker(Command.TimerFired(id))
        }
        Registry.log.info(
            "Proactive token refresh scheduled (target=${Registry.clock.isoTime(target)})"
        )
    }

    private fun launchRefresh(id: Long, allowImmediateRetry: Boolean) {
        state.refreshInFlight = true
        scope.safeLaunch {
            try {
                requestToken(
                    AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS,
                    forceRefresh = true,
                    refreshId = id
                )
                Registry.log.info("Proactive token refresh succeeded")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                postFromWorker(Command.RefreshFailed(id, e, allowImmediateRetry))
            }
        }
    }

    private fun armConnectivityWait(resumeImmediately: Boolean) {
        state.connectivityJob?.cancel()
        val id = ++state.connectivityId
        state.connectivityJob = scope.safeLaunch {
            val signal = CompletableDeferred<Unit>()
            val observer: NetworkObserver = { connected ->
                if (connected) signal.complete(Unit)
            }
            Registry.networkMonitor.onNetworkChange(observer)
            try {
                if (resumeImmediately && Registry.networkMonitor.isNetworkConnected()) {
                    signal.complete(Unit)
                }
                signal.await()
            } finally {
                Registry.networkMonitor.offNetworkChange(observer)
            }
            postFromWorker(Command.Connected(id))
        }
        Registry.log.info("AuthTokenManager: network failure — waiting for connectivity")
    }

    /**
     * True when [error] or one of its causes is a DNS failure or a socket-level failure (refused,
     * no route, reset). Timeouts are excluded.
     */
    private fun isConnectivityError(error: Throwable): Boolean =
        generateSequence(error) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .any { it is UnknownHostException || it is SocketException }

    private fun onForeground() {
        val cached = state.cachedToken
        val target = state.refreshAt
        when {
            state.resetPending -> Unit
            cached != null && !isStillValid(cached) -> {
                cacheToken(null)
                state.deliveryId++
                if (!state.refreshInFlight) {
                    retireRefresh()
                    startFetch()
                }
                Registry.log.info(
                    "AuthTokenManager: foreground transition (case=expired-cached-token)"
                )
            }
            target != null && Registry.clock.currentTimeMillis() >= target && !state.refreshInFlight -> {
                retireRefresh()
                Registry.log.info(PROACTIVE_REFRESH_FIRED)
                launchRefresh(state.refreshId, true)
                Registry.log.info("AuthTokenManager: foreground transition (case=missed-refresh)")
            }
            cached == null ->
                Registry.log.info("AuthTokenManager: foreground transition (case=no-cached-token)")
            else -> Registry.log.info("AuthTokenManager: foreground transition (case=still-valid)")
        }
    }

    private fun onLifecycleEvent(event: ActivityEvent) {
        event.takeIf<ActivityEvent.FirstStarted>() ?: return
        post(Command.Foreground)
    }

    private suspend fun invokeProvider(provider: AuthTokenProvider): String =
        suspendCancellableCoroutine { continuation ->
            val callback = object : AuthTokenProvider.Callback {
                override fun onSuccess(jwt: String) {
                    if (continuation.isActive) continuation.resume(jwt)
                }

                override fun onFailure(error: Throwable) {
                    if (!continuation.isActive) return
                    if (error is CancellationException) {
                        val wrapped = AuthTokenException.ProviderCancelled(error)
                        Registry.log.warning(requireNotNull(wrapped.message), error)
                        continuation.resumeWithException(wrapped)
                    } else {
                        continuation.resumeWithException(error)
                    }
                }
            }
            provider.fetchToken(callback)
        }

    private fun validateOrThrow(jwt: String): ValidatedToken =
        when (val result = JWTParser.parseAndValidate(jwt)) {
            is JWTValidationResult.Valid -> result.token
            else -> {
                val error = AuthTokenException.ValidationFailed(
                    result::class.simpleName ?: "Unknown"
                )
                Registry.log.error(requireNotNull(error.message), error)
                throw error
            }
        }

    private fun isStillValid(token: ValidatedToken): Boolean =
        Registry.clock.currentTimeMillis() / 1000L <
            token.expiresAtEpochSeconds - JWTParser.DEFAULT_LEEWAY_SECONDS

    /** Mutable data accessed only by the command consumer. */
    private class State {
        var provider: AuthTokenProvider? = null
        var identified = false
        var cachedToken: ValidatedToken? = null
        var generation = 0L
        var resetId = 0L
        var resetPending = false

        /**
         * Set on registration and cleared when a fetch result is accepted or the provider is
         * unregistered. While set, an identity change to identified or a matched clear starts a fetch.
         */
        var warmUpPending = false
        val waiters = mutableListOf<Waiter>()
        var fetchId = 0L
        var deliveryId = 0L
        var fetchJob: Job? = null
        var fetchAllowsImmediateRetry = true

        /** Set when a fetch fails offline while a refresh waiter is attached; read by RefreshFailed. */
        var refreshFetchFailedOffline = false
        var refreshId = 0L
        var refreshTimer: Clock.Cancellable? = null
        var refreshAt: Long? = null
        var refreshInFlight = false
        var connectivityId = 0L
        var connectivityJob: Job? = null
        val observers = mutableListOf<TokenRefreshObserver>()
    }

    private data class Waiter(
        val reply: CompletableDeferred<ValidatedToken>,
        val refreshId: Long?
    )

    private data class TokenSnapshot(val generation: Long, val token: ValidatedToken?)

    private data class Delivery(
        val id: Long,
        val generation: Long,
        val token: ValidatedToken,
        val observers: List<TokenRefreshObserver>
    )

    private class StaleRefreshException : CancellationException("Refresh superseded")

    /** Generation IDs pair each reset with its clear and reject superseded async completions. */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal sealed interface Command {
        data class Register(val generation: Long, val provider: AuthTokenProvider) : Command
        data class Unregister(val generation: Long) : Command
        data class Invalidate(val generation: Long) : Command
        data class Clear(
            val expectedGeneration: Long,
            val reply: CompletableDeferred<Unit>
        ) : Command
        data class Token(
            val reply: CompletableDeferred<ValidatedToken>,
            val forceRefresh: Boolean,
            val refreshId: Long?
        ) : Command
        data class Identity(val identified: Boolean) : Command
        data class CallerDone(val reply: CompletableDeferred<ValidatedToken>) : Command
        data class FetchDone(val id: Long, val result: Result<ValidatedToken>) : Command
        data class TimerFired(val id: Long) : Command
        data class RefreshFailed(
            val id: Long,
            val error: Exception,
            val allowImmediateRetry: Boolean
        ) : Command
        data class Connected(val id: Long) : Command
        data class CanDeliver(val id: Long, val reply: CompletableDeferred<Boolean>) : Command
        data object Foreground : Command
        data object RefreshRejected : Command
        data class Observe(val observer: TokenRefreshObserver) : Command
        data class Unobserve(val observer: TokenRefreshObserver) : Command
        data class ConnectivityJob(val reply: CompletableDeferred<Job?>) : Command
    }

    companion object {
        private const val MAX_CAUSE_DEPTH = 8
        private const val PROACTIVE_REFRESH_FIRED = "Proactive token refresh fired"

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

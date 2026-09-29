package com.klaviyo.core.auth

import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.LifecycleMonitor
import com.klaviyo.core.networking.NetworkObserver
import com.klaviyo.core.utils.takeIf
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal class KlaviyoAuthTokenManager(
    private val lifecycleMonitor: LifecycleMonitor = Registry.lifecycleMonitor
) : AuthTokenManager {

    internal val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Registry.dispatcher)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val state = State()

    init {
        scope.launch {
            try {
                for (command in commands) handle(command)
            } finally {
                retireWork()
                commands.close()
                val failure = CancellationException("Auth token manager stopped")
                state.waiters.forEach { it.completeExceptionally(failure) }
                while (true) {
                    when (val pending = commands.tryReceive().getOrNull() ?: break) {
                        is Command.Token -> pending.reply.completeExceptionally(failure)
                        is Command.Clear -> pending.reply.completeExceptionally(failure)
                        is Command.ConnectivityJob -> pending.reply.completeExceptionally(failure)
                        is Command.CanDeliver -> pending.reply.complete(false)
                        else -> Unit
                    }
                }
            }
        }
        lifecycleMonitor.onActivityEvent(::onLifecycleEvent)
    }

    override fun registerProvider(provider: AuthTokenProvider) {
        post(Command.Register(provider))
    }

    override fun unregisterProvider() {
        post(Command.Unregister)
    }

    override fun onTokenRefresh(observer: TokenRefreshObserver) {
        post(Command.Observe(observer))
    }

    override fun offTokenRefresh(observer: TokenRefreshObserver) {
        post(Command.Unobserve(observer))
    }

    override fun invalidate() {
        post(Command.Invalidate)
    }

    override suspend fun clearTokenState(onlyIfPendingReset: Boolean) {
        val reply = CompletableDeferred<Unit>()
        post(Command.Clear(onlyIfPendingReset, reply))
        reply.await()
    }

    override suspend fun currentToken(timeoutMs: Long): ValidatedToken =
        requestToken(timeoutMs)

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
        check(scope.coroutineContext[Job]?.isActive == true && commands.trySend(command).isSuccess) {
            "Auth token manager is closed"
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
                retireWork()
                state.provider = command.provider
                state.cachedToken = null
                state.resetPending = false
                Registry.log.info("AuthTokenProvider registered")
                startFetch()
            }
            Command.Unregister -> {
                retireWork()
                state.provider = null
                state.cachedToken = null
                state.resetPending = false
                failWaiters(AuthTokenException.NoProviderRegistered)
                Registry.log.info("AuthTokenProvider unregistered")
            }
            Command.Invalidate -> {
                retireWork()
                state.cachedToken = null
                state.resetPending = true
            }
            is Command.Clear -> {
                if (!command.onlyIfPendingReset || state.resetPending) {
                    retireWork()
                    state.cachedToken = null
                    state.resetPending = false
                    if (state.waiters.isNotEmpty()) startFetch()
                    Registry.log.info("Token state cleared")
                }
                command.reply.complete(Unit)
            }
            is Command.Token -> {
                if (command.refreshId != null && command.refreshId != state.refreshId) {
                    command.reply.completeExceptionally(CancellationException("Refresh superseded"))
                } else if (state.provider == null) {
                    command.reply.completeExceptionally(AuthTokenException.NoProviderRegistered)
                } else if (state.resetPending) {
                    state.waiters.add(command.reply)
                } else {
                    val cached = state.cachedToken?.takeIf { isStillValid(it) }
                    if (cached != null && !command.forceRefresh) {
                        command.reply.complete(cached)
                    } else {
                        state.waiters.add(command.reply)
                        startFetch()
                    }
                }
            }
            is Command.CallerDone -> state.waiters.remove(command.reply)
            is Command.FetchDone -> onFetchDone(command)
            is Command.TimerFired -> {
                if (command.id == state.refreshId && !state.resetPending) {
                    state.refreshFired = true
                    launchRefresh(command.id, true)
                }
            }
            is Command.RefreshFailed -> {
                if (command.id == state.refreshId && !state.resetPending) {
                    if (command.timerFired) state.refreshFired = false
                    Registry.log.warning(
                        "Proactive token refresh failed: ${command.error.javaClass.simpleName}",
                        command.error
                    )
                    if (command.error is IOException && state.provider != null) {
                        armConnectivityWait(command.allowImmediateRetry)
                    }
                }
            }
            is Command.Connected -> {
                if (command.id == state.connectivityId && !state.resetPending) {
                    state.connectivityJob = null
                    launchRefresh(state.refreshId, false)
                }
            }
            is Command.CanDeliver -> command.reply.complete(
                command.id == state.deliveryId && !state.resetPending
            )
            Command.Foreground -> onForeground()
            is Command.Observe -> state.observers.add(command.observer)
            is Command.Unobserve -> state.observers.remove(command.observer)
            is Command.ConnectivityJob -> command.reply.complete(state.connectivityJob)
        }
    }

    private fun startFetch() {
        if (state.fetchJob != null || state.resetPending) return
        val provider = state.provider ?: return
        val id = ++state.fetchId
        state.fetchJob = scope.launch {
            val result = runCatching {
                val jwt = invokeProvider(provider)
                validateOrThrow(jwt)
            }
            postFromWorker(Command.FetchDone(id, result))
        }
    }

    private fun onFetchDone(command: Command.FetchDone) {
        if (command.id != state.fetchId) return
        state.fetchJob = null
        command.result.fold(
            onSuccess = { token ->
                state.cachedToken = token
                scheduleRefresh(token)
                completeWaiters(token)
                val deliveryId = ++state.deliveryId
                Registry.log.info(
                    "Auth token acquired (exp=${token.expiresAtEpochSeconds}, iat=${token.issuedAtEpochSeconds})"
                )
                val observers = state.observers.toList()
                scope.launch {
                    observers.forEach { observer ->
                        val permission = CompletableDeferred<Boolean>()
                        if (!postFromWorker(Command.CanDeliver(deliveryId, permission))) return@launch
                        if (!permission.await()) return@launch
                        try {
                            observer(token.rawToken)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Registry.log.warning(
                                "TokenRefreshObserver threw ${e.javaClass.simpleName}",
                                e
                            )
                        }
                    }
                }
            },
            onFailure = ::failWaiters
        )
    }

    private fun completeWaiters(token: ValidatedToken) {
        state.waiters.forEach { it.complete(token) }
        state.waiters.clear()
    }

    private fun failWaiters(error: Throwable) {
        state.waiters.forEach { it.completeExceptionally(error) }
        state.waiters.clear()
    }

    private fun retireWork() {
        state.fetchId++
        state.deliveryId++
        state.fetchJob?.cancel()
        state.fetchJob = null
        state.refreshId++
        state.refreshTimer?.cancel()
        state.refreshTimer = null
        state.refreshAt = null
        state.refreshFired = false
        state.connectivityId++
        state.connectivityJob?.cancel()
        state.connectivityJob = null
    }

    private fun scheduleRefresh(token: ValidatedToken) {
        val now = Registry.clock.currentTimeMillis()
        val target = computeRefreshTarget(token, now)
        val id = ++state.refreshId
        state.refreshTimer?.cancel()
        state.refreshAt = target
        state.refreshFired = false
        state.refreshTimer = Registry.clock.schedule((target - now).coerceAtLeast(0)) {
            postFromWorker(Command.TimerFired(id))
        }
        Registry.log.info(
            "Proactive token refresh scheduled (target=${Registry.clock.isoTime(target)})"
        )
    }

    private fun launchRefresh(id: Long, allowImmediateRetry: Boolean) {
        Registry.log.info("Proactive token refresh fired")
        scope.launch {
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
                postFromWorker(
                    Command.RefreshFailed(id, e, allowImmediateRetry, allowImmediateRetry)
                )
            }
        }
    }

    private fun armConnectivityWait(resumeImmediately: Boolean) {
        state.connectivityJob?.cancel()
        val id = ++state.connectivityId
        state.connectivityJob = scope.launch {
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

    private fun onForeground() {
        val cached = state.cachedToken
        val target = state.refreshAt
        when {
            state.resetPending -> Unit
            cached != null && !isStillValid(cached) -> {
                state.cachedToken = null
                state.refreshId++
                state.refreshTimer?.cancel()
                state.refreshTimer = null
                state.refreshAt = null
                startFetch()
                Registry.log.info(
                    "AuthTokenManager: foreground transition (case=expired-cached-token)"
                )
            }
            target != null && Registry.clock.currentTimeMillis() >= target && !state.refreshFired -> {
                state.refreshId++
                state.refreshTimer?.cancel()
                state.refreshTimer = null
                state.refreshAt = null
                launchRefresh(state.refreshId, true)
                Registry.log.info("AuthTokenManager: foreground transition (case=missed-refresh)")
            }
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
                    if (continuation.isActive) continuation.resumeWithException(error)
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

    private class State {
        var provider: AuthTokenProvider? = null
        var cachedToken: ValidatedToken? = null
        var resetPending = false
        val waiters = mutableListOf<CompletableDeferred<ValidatedToken>>()
        var fetchId = 0L
        var deliveryId = 0L
        var fetchJob: Job? = null
        var refreshId = 0L
        var refreshTimer: Clock.Cancellable? = null
        var refreshAt: Long? = null
        var refreshFired = false
        var connectivityId = 0L
        var connectivityJob: Job? = null
        val observers = mutableListOf<TokenRefreshObserver>()
    }

    private sealed interface Command {
        data class Register(val provider: AuthTokenProvider) : Command
        data object Unregister : Command
        data object Invalidate : Command
        data class Clear(
            val onlyIfPendingReset: Boolean,
            val reply: CompletableDeferred<Unit>
        ) : Command
        data class Token(
            val reply: CompletableDeferred<ValidatedToken>,
            val forceRefresh: Boolean,
            val refreshId: Long?
        ) : Command
        data class CallerDone(val reply: CompletableDeferred<ValidatedToken>) : Command
        data class FetchDone(val id: Long, val result: Result<ValidatedToken>) : Command
        data class TimerFired(val id: Long) : Command
        data class RefreshFailed(
            val id: Long,
            val error: Exception,
            val timerFired: Boolean,
            val allowImmediateRetry: Boolean
        ) : Command
        data class Connected(val id: Long) : Command
        data class CanDeliver(val id: Long, val reply: CompletableDeferred<Boolean>) : Command
        data object Foreground : Command
        data class Observe(val observer: TokenRefreshObserver) : Command
        data class Unobserve(val observer: TokenRefreshObserver) : Command
        data class ConnectivityJob(val reply: CompletableDeferred<Job?>) : Command
    }

    companion object {
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

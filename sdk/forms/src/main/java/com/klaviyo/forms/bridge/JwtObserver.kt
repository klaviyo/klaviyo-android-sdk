package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenException
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.TokenRefreshObserver
import com.klaviyo.core.safeLaunch
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Delivers the auth token to the webview via [JsBridge.jwtMutation], independently of profile
 * delivery. Fetches a token when started, re-injects tokens acquired or refreshed while a form is
 * displayed, and fetches a token for the new profile on profile changes via [refetchToken].
 * Never injects an empty token.
 */
internal class JwtObserver : JsBridgeObserver {

    /**
     * Identity of the current session, replaced on each [startObserver] and null when stopped.
     * Injections capture it and re-check it on the UI thread so callbacks from a previous session
     * cannot reach a new webview.
     */
    @Volatile private var session: Any? = null

    private val scope = CoroutineScope(SupervisorJob() + Registry.dispatcher)

    private val fetchLock = Any()

    /** The in-flight token fetch. Guarded by [fetchLock]. */
    private var fetchJob: Job? = null

    /**
     * Stable instance so it can be unregistered by reference via [AuthTokenManager.offTokenRefresh].
     * Invoked on the manager's IO dispatcher.
     */
    private val refreshObserver: TokenRefreshObserver = { jwt -> onTokenRefreshed(jwt) }

    /**
     * Monotonic sequence claimed when an injection is requested. An injection applies only if its
     * sequence is higher than any already applied, so later requests win regardless of the order
     * their results reach the UI thread.
     */
    private val injectionSequence = AtomicLong(0L)

    /** Highest sequence applied to the webview. Only read/written on the UI thread. */
    private var lastInjectedSequence = 0L

    /**
     * Last token value injected into the current webview, used to skip duplicate injections within
     * a session. Null until the first injection of a session. Only read/written on the UI thread.
     */
    private var lastInjectedToken: String? = null

    /** Set by [startObserver] so the next injection on the UI thread clears [lastInjectedToken]. */
    @Volatile private var resetDedupOnNextInjection = false

    override fun startObserver() {
        resetDedupOnNextInjection = true
        val current = Any()
        session = current
        val sequence = injectionSequence.incrementAndGet()

        // off-then-on keeps a single registration across re-entrant starts
        Registry.get<AuthTokenManager>().apply {
            offTokenRefresh(refreshObserver)
            onTokenRefresh(refreshObserver)
        }

        fetchToken(current, AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS, sequence)
    }

    override fun stopObserver() {
        session = null
        Registry.get<AuthTokenManager>().offTokenRefresh(refreshObserver)
        synchronized(fetchLock) {
            fetchJob?.cancel()
            fetchJob = null
        }
    }

    /**
     * Cancel any in-flight fetch and fetch a token for the active profile with the background
     * timeout. Does nothing while stopped. The webview keeps its current token until the new one
     * is injected.
     */
    internal fun refetchToken() {
        val current = session ?: return
        fetchToken(
            current,
            AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS,
            injectionSequence.incrementAndGet()
        )
    }

    /**
     * Cancel any in-flight fetch and start a new one, unless [forSession] is no longer current.
     * The result is injected only if [forSession] is still current and the token is still the
     * manager's current token when it reaches the UI thread. A failed fetch injects nothing.
     */
    private fun fetchToken(forSession: Any, timeoutMs: Long, sequence: Long) {
        synchronized(fetchLock) {
            if (session !== forSession) return
            fetchJob?.cancel()
            fetchJob = scope.safeLaunch {
                val token = try {
                    Registry.get<AuthTokenManager>().currentToken(timeoutMs).rawToken
                } catch (e: CancellationException) {
                    throw e
                } catch (_: AuthTokenException.NoProviderRegistered) {
                    Registry.log.debug("Auth not enabled — no JWT to inject")
                    return@safeLaunch
                } catch (_: Exception) {
                    Registry.log.warning("Auth token fetch failed — no JWT injected")
                    return@safeLaunch
                }

                Registry.threadHelper.runOnUiThread {
                    if (session === forSession &&
                        Registry.get<AuthTokenManager>().isCurrentToken(token)
                    ) {
                        injectIfLatest(sequence, token)
                    }
                }
            }
        }
    }

    /**
     * Inject a token acquired or refreshed by the manager, if it is still the manager's current
     * token when it reaches the UI thread.
     */
    private fun onTokenRefreshed(jwt: String) {
        val current = session ?: return
        val sequence = injectionSequence.incrementAndGet()
        Registry.threadHelper.runOnUiThread {
            if (session === current && Registry.get<AuthTokenManager>().isCurrentToken(jwt)) {
                injectIfLatest(sequence, jwt)
            }
        }
    }

    /**
     * Inject [token] only if [sequence] is newer than any already applied, skipping the bridge call
     * when the value is unchanged within the session. Must be called on the UI thread.
     */
    private fun injectIfLatest(sequence: Long, token: String) {
        if (resetDedupOnNextInjection) {
            resetDedupOnNextInjection = false
            lastInjectedToken = null
        }
        if (sequence > lastInjectedSequence) {
            lastInjectedSequence = sequence
            if (token != lastInjectedToken) {
                lastInjectedToken = token
                Registry.get<JsBridge>().jwtMutation(token)
            }
        }
    }
}

package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenException
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.TokenRefreshObserver
import com.klaviyo.core.safeLaunch
import com.klaviyo.forms.webview.WebViewClient
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Delivers the auth token to the webview via [JsBridge.jwtMutation]. Fetches a token when started,
 * re-injects tokens acquired or refreshed while a form is displayed, and fetches a token for a
 * replaced profile once that profile is published via [publishProfile]. A token is only injected
 * after the profile of the current [AuthTokenManager.profileGeneration] has been published to the
 * webview. Never injects an empty token.
 */
internal class JwtObserver : JsBridgeObserver {

    /**
     * Identity of the current session, replaced on each [startObserver] and null when stopped.
     * Injections capture it and re-check it on the UI thread so callbacks from a previous session
     * cannot reach a new webview.
     */
    @Volatile private var session: Session? = null

    /** State scoped to one webview session. */
    private class Session {
        /** [AuthTokenManager.profileGeneration] when a token was last requested for this webview. */
        @Volatile var requestedGeneration = -1L

        /** True once a profile has been published to this webview via [publishProfile]. UI thread only. */
        var published = false

        /** [AuthTokenManager.profileGeneration] of the profile last published. UI thread only. */
        var publishedGeneration = -1L

        /** Set when a token was withheld because its profile was not yet published. UI thread only. */
        var injectionWithheld = false
    }

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

    /**
     * Set by [startObserver] and [refetchToken] so the next injection that passes the sequence
     * check on the UI thread resets [lastInjectedToken] to [dedupResetToken].
     */
    @Volatile private var resetDedupOnNextInjection = false

    /**
     * Value [lastInjectedToken] is reset to by the pending dedupe reset: the token already rendered
     * into the document by [WebViewClient.initialJwt] after [startObserver], or null after
     * [refetchToken] so the next token is always written.
     */
    @Volatile private var dedupResetToken: String? = null

    override fun startObserver() {
        dedupResetToken = Registry.getOrNull<WebViewClient>()?.initialJwt
        resetDedupOnNextInjection = true
        val current = Session()
        session = current
        val sequence = injectionSequence.incrementAndGet()

        // off-then-on keeps a single registration across re-entrant starts
        Registry.get<AuthTokenManager>().apply {
            offTokenRefresh(refreshObserver)
            onTokenRefresh(refreshObserver)
            current.requestedGeneration = profileGeneration()
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
     * is injected. The next token injected afterwards is always written to the webview, even if it
     * matches the previously injected value.
     */
    internal fun refetchToken() {
        val current = session ?: return
        dedupResetToken = null
        resetDedupOnNextInjection = true
        current.requestedGeneration = Registry.get<AuthTokenManager>().profileGeneration()
        fetchToken(
            current,
            AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS,
            injectionSequence.incrementAndGet()
        )
    }

    /**
     * Run [publish], which must send the active profile to the webview, then allow tokens for that
     * profile to be injected once the UI work queued so far has run. If the profile was replaced
     * since the last token request, or a token was withheld awaiting this publish, calls
     * [refetchToken].
     */
    internal fun publishProfile(publish: () -> Unit) {
        val current = session
        val generation = Registry.get<AuthTokenManager>().profileGeneration()
        publish()
        current ?: return
        Registry.threadHelper.runOnUiThread { onProfilePublished(current, generation) }
    }

    /** Must be called on the UI thread. */
    private fun onProfilePublished(forSession: Session, generation: Long) {
        if (session !== forSession) return
        if (!forSession.published || generation > forSession.publishedGeneration) {
            forSession.published = true
            forSession.publishedGeneration = generation
        }
        if (forSession.injectionWithheld || generation != forSession.requestedGeneration) {
            forSession.injectionWithheld = false
            refetchToken()
        }
    }

    /**
     * Cancel any in-flight fetch and start a new one, unless [forSession] is no longer current.
     * The result is injected only if [forSession] is still current and the token is still the
     * manager's current token when it reaches the UI thread. A failed fetch injects nothing.
     */
    private fun fetchToken(forSession: Session, timeoutMs: Long, sequence: Long) {
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
                } catch (_: AuthTokenException.NotIdentified) {
                    Registry.log.verbose("Profile not identified — no JWT to inject")
                    return@safeLaunch
                } catch (_: Exception) {
                    Registry.log.warning("Auth token fetch failed — no JWT injected")
                    return@safeLaunch
                }

                Registry.threadHelper.runOnUiThread {
                    if (session === forSession &&
                        Registry.get<AuthTokenManager>().isCurrentToken(token)
                    ) {
                        injectIfPublished(forSession, sequence, token)
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
                injectIfPublished(current, sequence, jwt)
            }
        }
    }

    /**
     * [injectIfLatest] if the profile of the current generation has been published to
     * [forSession]'s webview, otherwise withhold [token] until [publishProfile]. Must be called on
     * the UI thread.
     */
    private fun injectIfPublished(forSession: Session, sequence: Long, token: String) {
        val published = forSession.published &&
            forSession.publishedGeneration == Registry.get<AuthTokenManager>().profileGeneration()
        if (published) {
            injectIfLatest(sequence, token)
        } else {
            forSession.injectionWithheld = true
        }
    }

    /**
     * Inject [token] only if [sequence] is newer than any already applied, skipping the bridge call
     * when the value is unchanged since the last injection. A pending dedupe reset from
     * [startObserver] or [refetchToken] is consumed by the first injection that passes the sequence
     * check, which is then written unless it matches [dedupResetToken]. Must be called on the UI
     * thread.
     */
    private fun injectIfLatest(sequence: Long, token: String) {
        if (sequence <= lastInjectedSequence) return
        lastInjectedSequence = sequence
        if (resetDedupOnNextInjection) {
            resetDedupOnNextInjection = false
            lastInjectedToken = dedupResetToken
        }
        if (token != lastInjectedToken) {
            lastInjectedToken = token
            Registry.get<JsBridge>().jwtMutation(token)
        }
    }
}

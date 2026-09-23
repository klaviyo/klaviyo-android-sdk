package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenException
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.TokenInvalidationObserver
import com.klaviyo.core.auth.TokenRefreshObserver
import com.klaviyo.core.safeLaunch
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Delivers the auth token to the webview via [JsBridge.jwtMutation] independently of profile
 * delivery. It also subscribes to [AuthTokenManager.onTokenRefresh] so a token refreshed while a
 * form is displayed is re-injected into the webview.
 */
internal class JwtObserver : JsBridgeObserver {

    @Volatile private var stopped = false

    /**
     * Identity token for the current session, replaced on each [startObserver]. Both injection
     * paths capture it and re-check it once they reach the UI thread, so a callback queued in a
     * previous session (its fetch result, or a proactive-refresh echo) cannot inject into the fresh
     * webview a later session loaded. `@Volatile` because it is written off the UI thread by
     * [startObserver] and read on it.
     */
    @Volatile private var latestFetch: Any? = null

    private val scope = CoroutineScope(SupervisorJob() + Registry.dispatcher)
    private var fetchJob: Job? = null

    /**
     * Stable instance so it can be unregistered by reference via [AuthTokenManager.offTokenRefresh].
     * Invoked on the manager's IO dispatcher, so it hops to the UI thread before touching the bridge.
     */
    private val refreshObserver: TokenRefreshObserver = { jwt, isCurrent ->
        onTokenRefreshed(jwt, isCurrent)
    }
    private val invalidationObserver: TokenInvalidationObserver = { onTokenInvalidated() }

    /**
     * Monotonic sequence claimed by each token source when its injection is *requested*, not when it
     * resolves. The initial one-shot fetch reserves its slot up front in [startObserver]; the refresh
     * stream claims one each time it fires. Because the initial fetch takes its (lower) sequence
     * before it awaits, a proactive refresh that lands while the fetch is still in flight always
     * outranks it — so a slow or failed initial fetch can never clobber a fresher refreshed token.
     * Each injection applies only if it carries the highest sequence seen so far, so the newest token
     * always wins regardless of UI-callback ordering.
     */
    private val injectionSequence = AtomicLong(0L)

    /** Highest sequence applied to the webview. Only read/written on the UI thread. */
    private var lastInjectedSequence = 0L

    /**
     * Last token value injected into the *current* webview. Guards against re-injecting an
     * identical token within a session: a fast initial fetch that resolves within the interactive
     * budget delivers the same value both as its own result and via the
     * [AuthTokenManager.onTokenRefresh] echo that now fires on every acquisition. Null until the
     * first injection of a session, so an initial empty ("unauthenticated") token is still
     * delivered. Only read/written on the UI thread.
     */
    private var lastInjectedToken: String? = null

    /**
     * Set by [startObserver] so the next injection clears [lastInjectedToken] first. Each session
     * loads a fresh webview with no JWT, so the value-dedup must not carry over from a previous
     * session — otherwise a form reopened with an unchanged (still-valid) token would never receive
     * it. `@Volatile` because [startObserver] may run off the UI thread while injections run on it;
     * the flag is consumed on the UI thread so [lastInjectedToken] stays single-threaded.
     */
    @Volatile private var resetDedupOnNextInjection = false

    override fun startObserver() {
        stopped = false
        // A new session loads a fresh webview; forget the previous session's injected value so an
        // unchanged token is re-delivered rather than deduped away. Consumed on the UI thread.
        resetDedupOnNextInjection = true
        val thisFetch = Any()
        latestFetch = thisFetch
        // Reserve the initial fetch's place in the injection order now, at request time, so any
        // refresh that fires while the fetch is still in flight outranks it. Assigning the sequence
        // only once the token resolved let a slow or failed fetch clobber a fresher refreshed token.
        val fetchSequence = injectionSequence.incrementAndGet()
        // off-then-on guarantees a single registration across re-entrant starts (duplicate
        // registrations would inject the refreshed token more than once).
        Registry.get<AuthTokenManager>().apply {
            offTokenRefresh(refreshObserver)
            offTokenInvalidated(invalidationObserver)
            onTokenRefresh(refreshObserver)
            onTokenInvalidated(invalidationObserver)
        }

        fetchJob?.cancel()
        fetchJob = scope.safeLaunch {
            val token = try {
                Registry.get<AuthTokenManager>()
                    .currentToken(AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS)
                    .rawToken
            } catch (e: CancellationException) {
                throw e
            } catch (_: AuthTokenException.NoProviderRegistered) {
                Registry.log.debug("Auth not enabled — injecting empty JWT")
                null
            } catch (_: Exception) {
                Registry.log.warning("Auth token fetch failed — injecting empty JWT")
                null
            }

            Registry.threadHelper.runOnUiThread {
                if (latestFetch === thisFetch && !stopped) {
                    injectIfLatest(fetchSequence, token ?: "")
                }
            }
        }
    }

    override fun stopObserver() {
        stopped = true
        Registry.get<AuthTokenManager>().offTokenRefresh(refreshObserver)
        Registry.get<AuthTokenManager>().offTokenInvalidated(invalidationObserver)
        fetchJob?.cancel()
        fetchJob = null
    }

    internal fun clearToken() {
        val sequence = injectionSequence.incrementAndGet()
        val session = latestFetch
        Registry.threadHelper.runOnUiThread {
            if (!stopped && latestFetch === session) {
                injectIfLatest(sequence, "")
            }
        }
    }

    /**
     * Re-inject a proactively-refreshed token into the webview. The manager only notifies on a
     * successful fetch, so [jwt] is always a real (non-empty) token here. Captures the current
     * [latestFetch] and re-checks it (plus [stopped]) on the UI thread: a refresh dispatched during
     * a previous session could otherwise run after a stop/start cycle flipped [stopped] back to
     * false and inject a stale token into the freshly loaded webview.
     */
    private fun onTokenRefreshed(jwt: String, isCurrent: () -> Boolean) {
        val sequence = injectionSequence.incrementAndGet()
        val session = latestFetch
        Registry.threadHelper.runOnUiThread {
            if (!stopped && latestFetch === session && isCurrent()) {
                injectIfLatest(sequence, jwt)
            }
        }
    }

    private fun onTokenInvalidated() {
        val sequence = injectionSequence.incrementAndGet()
        val session = latestFetch
        Registry.threadHelper.runOnUiThread {
            if (!stopped && latestFetch === session) {
                injectIfLatest(sequence, "")
            }
        }
    }

    /**
     * Inject [token] only if [sequence] is newer than any already applied, so an out-of-order
     * initial-fetch callback cannot overwrite a fresher token delivered via the refresh stream.
     * Within a session, skips the bridge call when the value is unchanged from the last injection,
     * so a token delivered twice (e.g. a fast initial fetch plus its refresh-stream echo) hits the
     * webview once; a new session first clears that baseline (see [resetDedupOnNextInjection]) so a
     * reopened form still receives an unchanged token. Must be called on the UI thread, where
     * [lastInjectedSequence] and [lastInjectedToken] are exclusively accessed.
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

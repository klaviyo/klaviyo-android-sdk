package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenException
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.TokenRefreshObserver
import com.klaviyo.core.safeLaunch
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Delivers the auth token to the webview via [JsBridge.jwtMutation] at [NativeBridgeMessage.JsReady],
 * before [ProfileMutationObserver] injects profile identifiers at HandShook.
 *
 * The onsite personalization module only triggers the authenticated profile fetch when both a JWT
 * and profile identifiers are present, so the JWT must land first.
 *
 * Beyond the initial delivery, this observer subscribes to [AuthTokenManager.onTokenRefresh] so that
 * a token proactively refreshed while a form is displayed is re-injected into the webview, keeping
 * onsite from acting on a stale (eventually expired) JWT.
 */
internal class JwtObserver : JsBridgeObserver {

    /**
     * Completes once the JWT has been delivered. [ProfileMutationObserver] awaits this before
     * injecting profile identifiers. Reused while still pending so a re-entrant start does not
     * orphan a waiter that captured the previous reference.
     */
    @Volatile
    internal var jwtReady: CompletableDeferred<Unit> = CompletableDeferred()
        private set

    @Volatile private var stopped = false

    @Volatile private var latestFetch: Any? = null

    private val scope = CoroutineScope(SupervisorJob() + Registry.dispatcher)
    private var fetchJob: Job? = null

    /**
     * Stable instance so it can be unregistered by reference via [AuthTokenManager.offTokenRefresh].
     * Invoked on the manager's IO dispatcher, so it hops to the UI thread before touching the bridge.
     */
    private val refreshObserver: TokenRefreshObserver = { jwt -> onTokenRefreshed(jwt) }

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
     * Last token value actually injected. Guards against re-injecting an identical token: a fast
     * initial fetch that resolves within the interactive budget delivers the same token both as its
     * own result and via the [AuthTokenManager.onTokenRefresh] broadcast that now fires on every
     * acquisition. Null until the first injection, so an initial empty ("unauthenticated") token is
     * still delivered. Only read/written on the UI thread.
     */
    private var lastInjectedToken: String? = null

    override fun startObserver() {
        stopped = false
        val thisFetch = Any()
        latestFetch = thisFetch
        // Reserve the initial fetch's place in the injection order now, at request time, so any
        // refresh that fires while the fetch is still in flight outranks it. Assigning the sequence
        // only once the token resolved let a slow or failed fetch clobber a fresher refreshed token.
        val fetchSequence = injectionSequence.incrementAndGet()
        val currentJwtReady = if (jwtReady.isCompleted) {
            CompletableDeferred<Unit>().also { jwtReady = it }
        } else {
            jwtReady
        }

        // off-then-on guarantees a single registration across re-entrant starts (duplicate
        // registrations would inject the refreshed token more than once).
        Registry.get<AuthTokenManager>().apply {
            offTokenRefresh(refreshObserver)
            onTokenRefresh(refreshObserver)
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
                    currentJwtReady.complete(Unit)
                }
            }
        }
    }

    override fun stopObserver() {
        stopped = true
        Registry.get<AuthTokenManager>().offTokenRefresh(refreshObserver)
        fetchJob?.cancel()
        fetchJob = null
    }

    /**
     * Re-inject a proactively-refreshed token into the webview. The manager only notifies on a
     * successful fetch, so [jwt] is always a real (non-empty) token here. Skips if the observer has
     * been stopped to avoid touching a torn-down webview.
     */
    private fun onTokenRefreshed(jwt: String) {
        val sequence = injectionSequence.incrementAndGet()
        Registry.threadHelper.runOnUiThread {
            if (!stopped) {
                injectIfLatest(sequence, jwt)
            }
        }
    }

    /**
     * Inject [token] only if [sequence] is newer than any already applied, so an out-of-order
     * initial-fetch callback cannot overwrite a fresher token delivered via the refresh stream.
     * Skips the bridge call when the value is unchanged from the last injection, so a token
     * delivered twice (e.g. a fast initial fetch plus its refresh-stream echo) hits the webview
     * once. Must be called on the UI thread, where [lastInjectedSequence] and [lastInjectedToken]
     * are exclusively accessed.
     */
    private fun injectIfLatest(sequence: Long, token: String) {
        if (sequence > lastInjectedSequence) {
            lastInjectedSequence = sequence
            if (token != lastInjectedToken) {
                lastInjectedToken = token
                Registry.get<JsBridge>().jwtMutation(token)
            }
        }
    }
}

package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenException
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.safeLaunch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Delivers the auth token to the webview via [JsBridge.jwtMutation] as soon as the JS bridge
 * script is ready ([NativeBridgeMessage.JsReady]), which fires before the handshake phase where
 * [ProfileMutationObserver] injects profile identifiers.
 *
 * Ordering matters: the onsite personalization module only triggers the authenticated profile
 * fetch when both a JWT and profile identifiers are present. Delivering the JWT at JsReady (before
 * profile at HandShook) guarantees the token is in place when identifiers arrive.
 *
 * [jwtReady] is a [CompletableDeferred] that completes after the JWT is injected so
 * [ProfileMutationObserver] can await it before injecting profile identifiers, eliminating the
 * residual race where a slow async token fetch outlasts the JsReady→HandShook window. A fresh
 * deferred is allocated only when the previous one has settled; a still-pending deferred is reused
 * across [startObserver] calls so any waiter that captured it is not orphaned by a double-start.
 *
 * Live token refresh delivery on rotation is tracked in MAGE-630.
 */
internal class JwtObserver : JsBridgeObserver {
    // startOn defaults to NativeBridgeMessage.JsReady — intentionally earlier than
    // ProfileMutationObserver (HandShook) so the JWT is set before profile identifiers.

    /**
     * Deferred that completes once the JWT has been delivered for the current WebView session.
     * Reused across [startObserver] calls while still pending so [ProfileMutationObserver] waiters
     * that captured an earlier reference are not orphaned. Replaced with a fresh instance only
     * after the previous deferred has settled (completed or cancelled).
     */
    @Volatile
    internal var jwtReady: CompletableDeferred<Unit> = CompletableDeferred()
        private set

    // Guards against a queued runOnUiThread callback from a previous session delivering a stale
    // JWT after stopObserver. Cooperative cancellation of fetchJob only works at suspension
    // points; the post-suspension path (after currentToken returns) needs this explicit flag.
    @Volatile private var stopped = false

    private val scope = CoroutineScope(SupervisorJob() + Registry.dispatcher)
    private var fetchJob: Job? = null

    override fun startObserver() {
        stopped = false
        // Reuse jwtReady if still pending. Replacing it would orphan any ProfileMutationObserver
        // waiter that captured the previous instance during the JsReady→HandShook window. The
        // fetchJob cancellation below ensures only the newest fetch's token completes the deferred:
        // the prior fetch either bails at a suspension point (cancellation) or no-ops on the
        // !isCompleted guard inside the UI callback.
        val currentJwtReady = if (jwtReady.isCompleted) {
            CompletableDeferred<Unit>().also { jwtReady = it }
        } else {
            jwtReady
        }

        fetchJob?.cancel() // guard against double-start without an intervening stopObserver
        fetchJob = scope.safeLaunch {
            val token = try {
                Registry.get<AuthTokenManager>()
                    .currentToken(AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS)
                    .rawToken
            } catch (e: CancellationException) {
                throw e // Propagate coroutine cancellation
            } catch (_: AuthTokenException.NoProviderRegistered) {
                Registry.log.debug("Auth not enabled — injecting empty JWT")
                null
            } catch (_: Exception) {
                Registry.log.warning("Auth token fetch failed — injecting empty JWT")
                null
            }

            Registry.threadHelper.runOnUiThread {
                // !isCompleted: a later fetch may have already completed this deferred (race
                // between two queued UI callbacks when the deferred is reused across starts).
                // !stopped: stopObserver may have run while we were suspended on currentToken.
                // Together these prevent a stale callback from injecting into a destroyed or
                // superseded WebView session.
                if (!currentJwtReady.isCompleted && !stopped) {
                    Registry.get<JsBridge>().jwtMutation(token ?: "")
                    currentJwtReady.complete(Unit)
                }
            }
        }
    }

    override fun stopObserver() {
        stopped = true
        fetchJob?.cancel()
        fetchJob = null
        // Do NOT cancel jwtReady here — ProfileMutationObserver cancels its own initJob in
        // stopObserver, which unblocks any await on jwtReady without poisoning the deferred
        // for a potential subsequent session.
    }
}

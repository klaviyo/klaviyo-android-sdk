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
 * [jwtReady] is a fresh [CompletableDeferred] created on each [startObserver] call. It is
 * completed after the JWT is injected so that [ProfileMutationObserver] can await it before
 * injecting profile identifiers, eliminating the residual race where a slow async token fetch
 * outlasts the JsReady→HandShook window. A new deferred is created each session so that reinit
 * after [stopObserver] always starts from a clean state.
 *
 * Live token refresh delivery on rotation is tracked in MAGE-630.
 */
internal class JwtObserver : JsBridgeObserver {
    // startOn defaults to NativeBridgeMessage.JsReady — intentionally earlier than
    // ProfileMutationObserver (HandShook) so the JWT is set before profile identifiers.

    /**
     * Deferred that completes once the JWT has been delivered for the current WebView session.
     * Replaced with a fresh instance on every [startObserver] call, so [ProfileMutationObserver]
     * always awaits the deferred that corresponds to the active session.
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
        // Fresh deferred for this WebView session. ProfileMutationObserver reads jwtReady via
        // its JwtObserver reference in its own startObserver, so it always gets this new instance.
        val currentJwtReady = CompletableDeferred<Unit>()
        jwtReady = currentJwtReady

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
                // Double-guard: check both that this session's deferred is still current (i.e.
                // startObserver hasn't been called again) and that stop wasn't requested. This
                // prevents a stale runOnUiThread callback — queued after currentToken returned
                // but before stopObserver ran — from injecting into a destroyed or new WebView.
                if (jwtReady === currentJwtReady && !stopped) {
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

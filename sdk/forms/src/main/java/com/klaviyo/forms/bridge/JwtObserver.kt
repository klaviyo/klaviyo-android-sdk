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
 * [jwtReady] is completed after the JWT is injected (or cancelled on [stopObserver]) so that
 * [ProfileMutationObserver] can await it before injecting profile identifiers, eliminating the
 * residual race where a slow async token fetch outlasts the JsReady→HandShook window.
 *
 * Live token refresh delivery on rotation is tracked in MAGE-630.
 */
internal class JwtObserver(
    internal val jwtReady: CompletableDeferred<Unit> = CompletableDeferred()
) : JsBridgeObserver {
    // startOn defaults to NativeBridgeMessage.JsReady — intentionally earlier than
    // ProfileMutationObserver (HandShook) so the JWT is set before profile identifiers.

    private val scope = CoroutineScope(SupervisorJob() + Registry.dispatcher)
    private var fetchJob: Job? = null

    override fun startObserver() {
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
                Registry.get<JsBridge>().jwtMutation(token ?: "")
                jwtReady.complete(Unit)
            }
        }
    }

    override fun stopObserver() {
        fetchJob?.cancel()
        fetchJob = null
        // Cancel the deferred so ProfileMutationObserver doesn't hang if the WebView is
        // destroyed before the token fetch completes.
        jwtReady.cancel()
    }
}

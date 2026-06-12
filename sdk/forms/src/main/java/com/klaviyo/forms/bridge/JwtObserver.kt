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
 * Delivers the auth token to the webview via [JsBridge.jwtMutation] at [NativeBridgeMessage.JsReady],
 * before [ProfileMutationObserver] injects profile identifiers at HandShook.
 *
 * The onsite personalization module only triggers the authenticated profile fetch when both a JWT
 * and profile identifiers are present, so the JWT must land first.
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

    override fun startObserver() {
        stopped = false
        val thisFetch = Any()
        latestFetch = thisFetch
        val currentJwtReady = if (jwtReady.isCompleted) {
            CompletableDeferred<Unit>().also { jwtReady = it }
        } else {
            jwtReady
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
    }
}

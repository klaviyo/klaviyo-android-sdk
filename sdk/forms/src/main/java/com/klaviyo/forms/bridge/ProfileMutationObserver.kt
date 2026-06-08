package com.klaviyo.forms.bridge

import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry
import com.klaviyo.core.safeLaunch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Observe [State] in the analytics package to synchronize profile identifiers with the webview
 */
internal class ProfileMutationObserver(
    private val jwtReady: Deferred<Unit>? = null
) : JsBridgeObserver, StateChangeObserver {

    private val scope = CoroutineScope(SupervisorJob() + Registry.dispatcher)

    /**
     * Start on [NativeBridgeMessage.HandShook] rather than the default [NativeBridgeMessage.JsReady]
     * so the initial profile injection fires *after* [JwtObserver] has delivered the JWT at JsReady.
     *
     * The onsite personalization module only triggers the authenticated profile fetch when both a
     * JWT and profile identifiers are present. If profile is injected before the JWT, the module
     * sees identifiers with no token and never makes the authenticated fetch.
     *
     * When [jwtReady] is provided, the initial [injectProfile] call additionally awaits that
     * deferred before injecting, eliminating the residual race where a slow async token fetch
     * completes after [NativeBridgeMessage.HandShook] fires.
     */
    override val startOn: NativeBridgeMessage get() = NativeBridgeMessage.HandShook

    override fun startObserver() {
        val deferred = jwtReady
        if (deferred != null) {
            scope.safeLaunch {
                try {
                    deferred.await()
                } catch (e: CancellationException) {
                    throw e // WebView is being torn down — do not inject
                }
                injectAndSubscribe()
            }
        } else {
            injectAndSubscribe()
        }
    }

    override fun stopObserver() {
        scope.cancel()
        Registry.get<State>().offStateChange(this)
    }

    /**
     * Update profile in webview whenever an identifier changes, or profile is reset
     */
    override fun invoke(change: StateChange) {
        when (change) {
            is StateChange.ProfileIdentifier, is StateChange.ProfileReset -> injectProfile()
            else -> Unit
        }
    }

    private fun injectAndSubscribe() {
        injectProfile()
        Registry.get<State>().onStateChange(this)
    }

    private fun injectProfile() = Registry.get<JsBridge>().profileMutation(
        Registry.get<State>().getAsProfile()
    )
}

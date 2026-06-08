package com.klaviyo.forms.bridge

import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry

/**
 * Observe [State] in the analytics package to synchronize profile identifiers with the webview
 */
internal class ProfileMutationObserver : JsBridgeObserver, StateChangeObserver {

    /**
     * Start on [NativeBridgeMessage.HandShook] rather than the default [NativeBridgeMessage.JsReady]
     * so the initial profile injection fires *after* [JwtObserver] has delivered the JWT at JsReady.
     *
     * The onsite personalization module only triggers the authenticated profile fetch when both a
     * JWT and profile identifiers are present. If profile is injected before the JWT, the module
     * sees identifiers with no token and never makes the authenticated fetch.
     */
    override val startOn: NativeBridgeMessage get() = NativeBridgeMessage.HandShook

    override fun startObserver() {
        // Set initial profile identifiers on startup
        injectProfile()
        Registry.get<State>().onStateChange(this)
    }

    override fun stopObserver() = Registry.get<State>().offStateChange(this)

    /**
     * Update profile in webview whenever an identifier changes, or profile is reset
     */
    override fun invoke(change: StateChange) {
        when (change) {
            is StateChange.ProfileIdentifier, is StateChange.ProfileReset -> injectProfile()
            else -> Unit
        }
    }

    private fun injectProfile() = Registry.get<JsBridge>().profileMutation(
        Registry.get<State>().getAsProfile()
    )
}

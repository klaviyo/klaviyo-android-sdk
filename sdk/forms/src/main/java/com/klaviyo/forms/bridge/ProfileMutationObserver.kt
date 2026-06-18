package com.klaviyo.forms.bridge

import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry

/**
 * Observe [State] in the analytics package to synchronize profile identifiers with the webview
 */
internal class ProfileMutationObserver : JsBridgeObserver, StateChangeObserver {

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

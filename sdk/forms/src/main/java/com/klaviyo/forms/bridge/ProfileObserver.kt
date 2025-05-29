package com.klaviyo.forms.bridge

import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.core.Registry

/**
 * Observe [State] in the analytics package to synchronize profile identifiers with the webview
 */
internal class ProfileObserver : JsBridgeObserver {
    override val handshake: HandshakeSpec = HandshakeSpec(
        type = "profileMutation",
        version = 1
    )

    override fun startObserver() {
        // Set initial profile identifiers on startup
        injectProfile()
        Registry.get<State>().onStateChange(::onStateChange)
    }

    override fun stopObserver() = Registry.get<State>().offStateChange(::onStateChange)

    /**
     * Update profile in webview whenever an identifier changes, or profile is reset
     */
    private fun onStateChange(change: StateChange) = when (change) {
        is StateChange.ProfileIdentifier, is StateChange.ProfileReset -> injectProfile()
        else -> Unit
    }

    private fun injectProfile() = Registry.get<JsBridge>().setProfile(
        Registry.get<State>().getAsProfile()
    )
}

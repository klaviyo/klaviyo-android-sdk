package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Keyword
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry

/**
 * Observe [State] in the analytics package to synchronize profile identifiers with the webview
 */
internal class ProfileObserver : Observer {
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
    private fun onStateChange(key: Keyword?, oldValue: Any?) = when (key?.name) {
        in ProfileKey.IDENTIFIERS, null -> injectProfile()
        else -> Unit
    }

    private fun injectProfile() = Registry.get<OnsiteBridge>().setProfile(
        Registry.get<State>().getAsProfile()
    )
}

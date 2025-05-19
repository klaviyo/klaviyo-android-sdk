package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Keyword
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

    override fun startObserver() = Registry.get<State>().onStateChange(::onStateChange).also {
        onStateChange(null, null)
    }

    override fun stopObserver() = Registry.get<State>().offStateChange(::onStateChange)

    private fun onStateChange(key: Keyword?, v: Any?) = when (key?.name) {
        in IDENTIFIERS, null -> Registry.get<OnsiteBridge>().setProfile(
            Registry.get<State>().getAsProfile()
        )
        else -> Unit
    }

    companion object {
        private val IDENTIFIERS = listOf(
            "external_id",
            "email",
            "phone_number",
            "anonymous_id"
        )
    }
}

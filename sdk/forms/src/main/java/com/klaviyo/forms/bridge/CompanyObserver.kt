package com.klaviyo.forms.bridge

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Keyword
import com.klaviyo.analytics.model.StateKey
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.forms.reInitializeInAppForms

/**
 * When the company changes, the whole webview needs to be reinitialized
 * to reload klaviyo.js for the new company
 */
internal class CompanyObserver : JsBridgeObserver {
    /**
     * At this time, company ID doesn't have a handshake spec because
     * it only resets the webview, doesn't communicate with the onsite module
     */
    override val handshake: HandshakeSpec? = null

    override fun startObserver() = Registry.get<State>().onStateChange(::onStateChange)

    override fun stopObserver() = Registry.get<State>().offStateChange(::onStateChange)

    private fun onStateChange(key: Keyword?, oldValue: Any?) = when (key) {
        StateKey.API_KEY -> Klaviyo.reInitializeInAppForms().run {
            Registry.log.info(
                "In-app forms reinitialized: company ID changed from $oldValue to ${Registry.get<State>().apiKey}"
            )
        }

        else -> Unit
    }
}

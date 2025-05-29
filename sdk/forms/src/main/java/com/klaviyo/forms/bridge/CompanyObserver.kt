package com.klaviyo.forms.bridge

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
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

    private fun onStateChange(change: StateChange) = when (change) {
        is StateChange.ApiKey -> Klaviyo.reInitializeInAppForms().run {
            Registry.log.info(
                "In-app forms reinitialized: company ID changed from ${change.oldValue} to ${Registry.get<State>().apiKey}"
            )
        }

        else -> Unit
    }
}

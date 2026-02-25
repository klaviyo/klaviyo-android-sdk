package com.klaviyo.forms.bridge

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry
import com.klaviyo.forms.reInitializeInAppForms

/**
 * When the company changes, the whole webview needs to be reinitialized
 * to reload klaviyo.js for the new company
 */
internal class CompanyObserver : JsBridgeObserver, StateChangeObserver {

    override fun startObserver() = Registry.get<State>().onStateChange(this)

    override fun stopObserver() = Registry.get<State>().offStateChange(this)

    override fun invoke(change: StateChange) {
        when (change) {
            is StateChange.ApiKey -> Klaviyo.reInitializeInAppForms().run {
                Registry.log.debug(
                    "In-app forms reinitialized: company ID changed from ${change.oldValue} to ${Registry.get<State>().apiKey}"
                )
            }

            else -> Unit
        }
    }
}

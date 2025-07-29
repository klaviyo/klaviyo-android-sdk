package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.ProfileEventObserver
import com.klaviyo.core.Registry

/**
 * Observe events sent in the analytics package to trigger forms in the webview
 */
internal class FormsProfileEventObserver : JsBridgeObserver, ProfileEventObserver {

    override fun startObserver() {
        Registry.get<ApiClient>().onProfileEvent(this)
    }

    override fun stopObserver() {
        Registry.get<ApiClient>().offProfileEvent(this)
    }

    override fun invoke(event: Event) {
        Registry.get<JsBridge>().profileEvent(event)
    }
}

package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.state.ProfileEventObserver
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.TrackedEventsBuffer
import com.klaviyo.core.Registry

/**
 * Observe events sent in the analytics package to trigger forms in the webview
 */
internal class FormsProfileEventObserver : JsBridgeObserver, ProfileEventObserver {

    override fun startObserver() {
        Registry.get<State>().onProfileEvent(this)
        TrackedEventsBuffer.apply {
            getValidEvents().forEach { invoke(it) }
            clearBuffer()
        }
    }

    override fun stopObserver() {
        Registry.get<State>().offProfileEvent(this)
    }

    override fun invoke(event: Event) {
        Registry.get<JsBridge>().profileEvent(event)
    }
}

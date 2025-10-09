package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.state.GenericEventBuffer
import com.klaviyo.analytics.state.ProfileEventObserver
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry

/**
 * Observe events sent in the analytics package to trigger forms in the webview
 */
internal class FormsProfileEventObserver : JsBridgeObserver, ProfileEventObserver {

    override fun startObserver() {
        // Send buffered events to JS (enriched with uuid and _time)
        GenericEventBuffer.getEvents().forEach { event ->
            invoke(event)
        }
        // Register for future events
        Registry.get<State>().onProfileEvent(this)
    }

    override fun stopObserver() {
        Registry.get<State>().offProfileEvent(this)
    }

    override fun invoke(event: Event) {
        Registry.get<JsBridge>().profileEvent(event)
    }
}

package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.state.ProfileEventObserver
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry

/**
 * Observe events sent in the analytics package to trigger forms in the webview
 */
internal class ProfileEventObserver : JsBridgeObserver, ProfileEventObserver {

    override val startOn: NativeBridgeMessage
        get() = NativeBridgeMessage.HandShook

    override fun startObserver() {
        // Send buffered events to JS (enriched with uuid and _time)
        Registry.get<State>().getBufferedEvents().forEach { event ->
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

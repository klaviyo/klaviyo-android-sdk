package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.state.ProfileEventObserver
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry

/**
 * Observe events sent in the analytics package to trigger forms in the webview
 */
internal class FormsProfileEventObserver : JsBridgeObserver, ProfileEventObserver {

    override fun startObserver() {
        Registry.get<State>().onProfileEvent(this)
        // TODO(forms-buffer): After registering as an observer, check FormsTriggerBuffer for any buffered events
        // Replay each buffered event through invoke(event) to maintain existing flow
        // This handles events that arrived before JS was ready (e.g., push opens before initialization)
    }

    override fun stopObserver() {
        Registry.get<State>().offProfileEvent(this)
    }

    override fun invoke(event: Event) {
        Registry.get<JsBridge>().profileEvent(event)
    }
}

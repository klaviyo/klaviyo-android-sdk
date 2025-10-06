package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.state.FormsTriggerBuffer
import com.klaviyo.analytics.state.ProfileEventObserver
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry

/**
 * Observe events sent in the analytics package to trigger forms in the webview
 */
internal class FormsProfileEventObserver : JsBridgeObserver, ProfileEventObserver {

    override fun startObserver() {
        Registry.log.info(
            "FormsProfileEventObserver: Starting observer - registering for profile events"
        )
        Registry.get<State>().onProfileEvent(this)

        // Replay any buffered events that arrived before JS was ready
        // (e.g., push opens before initialization or registerForInAppForms)
        Registry.log.debug("FormsProfileEventObserver: Checking for buffered events to replay")
        val bufferedEvents = FormsTriggerBuffer.getValidEvents()

        if (bufferedEvents.isNotEmpty()) {
            Registry.log.info(
                "FormsProfileEventObserver: Replaying ${bufferedEvents.size} buffered event(s) to forms module"
            )
            bufferedEvents.forEach { event ->
                Registry.log.info(
                    "FormsProfileEventObserver: Replaying buffered event ${event.metric.name} to JS bridge"
                )
                invoke(event)
            }
            Registry.log.info("FormsProfileEventObserver: Finished replaying all buffered events")
        } else {
            Registry.log.debug("FormsProfileEventObserver: No buffered events to replay")
        }
    }

    override fun stopObserver() {
        Registry.log.info(
            "FormsProfileEventObserver: Stopping observer - unregistering from profile events"
        )
        Registry.get<State>().offProfileEvent(this)
    }

    override fun invoke(event: Event) {
        Registry.log.debug(
            "FormsProfileEventObserver: Forwarding event ${event.metric.name} to JS bridge"
        )
        Registry.get<JsBridge>().profileEvent(event)
    }
}

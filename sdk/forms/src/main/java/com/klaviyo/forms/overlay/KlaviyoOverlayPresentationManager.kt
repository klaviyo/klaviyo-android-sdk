package com.klaviyo.forms.overlay

import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent

/**
 * Coordinates preloading klaviyo.js and presentation forms in an overlay activity
 */
internal class KlaviyoOverlayPresentationManager() : OverlayPresentationManager {

    /**
     * For tracking device rotation
     */
    private var orientation: Int? = null

    init {
        Registry.lifecycleMonitor.onActivityEvent(::onActivityEvent)
    }

    /**
     * This closes the form on rotation, which we can detect with the local field
     * We wait for a change, see if it's different from the current, and close an open webview
     *
     * TODO handle rotation better!
     */
    private fun onActivityEvent(event: ActivityEvent) {
        if (event is ActivityEvent.ConfigurationChanged) {
            val newOrientation = event.newConfig.orientation
            if (orientation != newOrientation) {
                Registry.log.debug("New screen orientation, closing form")
                dismissOverlay()
            }
            orientation = newOrientation
        }
    }

    /**
     * Launch the overlay activity
     */
    override fun presentOverlay() {
        Registry.lifecycleMonitor.currentActivity.takeIf { it !is KlaviyoFormsOverlayActivity }?.run {
            Registry.config.applicationContext.startActivity(
                KlaviyoFormsOverlayActivity.launchIntent
            )
        }
    }

    /**
     * Detach the webview from the activity and finish
     */
    override fun dismissOverlay() {
        Registry.lifecycleMonitor.currentActivity.takeIf { it is KlaviyoFormsOverlayActivity }?.finish()
    }
}

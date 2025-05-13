package com.klaviyo.forms.presentation

import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent

/**
 * Coordinates preloading klaviyo.js and presentation forms in an overlay activity
 */
internal class KlaviyoPresentationManager() : PresentationManager {

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
     * TODO handle rotation better, including enum or typealias for orientation.
     */
    private fun onActivityEvent(event: ActivityEvent) {
        if (event is ActivityEvent.ConfigurationChanged) {
            val newOrientation = event.newConfig.orientation
            if (orientation != newOrientation) {
                Registry.log.debug("New screen orientation, closing form")
                dismiss()
            }
            orientation = newOrientation
        }
    }

    /**
     * Launch the overlay activity
     */
    override fun present() = if (Registry.lifecycleMonitor.currentActivity !is KlaviyoFormsOverlayActivity) {
        Registry.config.applicationContext.startActivity(KlaviyoFormsOverlayActivity.launchIntent)
    } else {
        Registry.log.debug("Form Overlay Activity is already presented")
    }

    /**
     * Detach the webview from the activity and finish
     */
    override fun dismiss() {
        Registry.lifecycleMonitor.currentActivity.takeIf {
            it is KlaviyoFormsOverlayActivity
        }?.finish()
    }
}

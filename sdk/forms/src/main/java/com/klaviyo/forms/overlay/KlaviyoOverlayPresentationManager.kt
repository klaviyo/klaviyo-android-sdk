package com.klaviyo.forms.overlay

import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.forms.webview.WebViewClient

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

    override fun presentOverlay() {
        Registry.lifecycleMonitor.currentActivity?.let {
            Registry.get<WebViewClient>().attachWebView(
                it
            )
        } ?: run {
            Registry.log.warning("Unable to show IAF - null activity reference")
        }
    }

    override fun dismissOverlay() {
        Registry.lifecycleMonitor.currentActivity?.let {
            Registry.get<WebViewClient>().detachWebView(
                it
            )
        } ?: run {
            Registry.log.warning("Unable to dismiss IAF - null activity reference")
        }
    }
}

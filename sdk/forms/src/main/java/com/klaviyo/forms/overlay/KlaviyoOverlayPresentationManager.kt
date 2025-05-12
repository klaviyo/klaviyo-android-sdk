package com.klaviyo.forms.overlay

import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.forms.bridge.BridgeMessageHandler
import com.klaviyo.forms.bridge.KlaviyoBridgeMessageHandler
import com.klaviyo.forms.webview.KlaviyoWebViewClient
import com.klaviyo.forms.webview.WebViewClient

/**
 * Coordinates preloading klaviyo.js and presentation forms in an overlay activity
 */
internal class KlaviyoOverlayPresentationManager() : OverlayPresentationManager {

    private val bridgeMessageHandler: BridgeMessageHandler = KlaviyoBridgeMessageHandler(this).apply {
        Registry.register<BridgeMessageHandler>(this)
    }

    private val webViewClient: WebViewClient = KlaviyoWebViewClient(bridgeMessageHandler).apply {
        Registry.register<WebViewClient>(this)
    }

    /**
     * For tracking device rotation
     */
    private var orientation: Int? = null

    init {
        Registry.lifecycleMonitor.onActivityEvent(::onActivityEvent)
    }

    override fun initialize() {
        webViewClient.initializeWebView()
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
        val currentActivity = Registry.lifecycleMonitor.currentActivity
        if (currentActivity !is KlaviyoFormsOverlayActivity) {
            Registry.config.applicationContext.startActivity(
                KlaviyoFormsOverlayActivity.launchIntent
            )
        }
    }

    /**
     * Detach the webview from the activity and finish
     * TODO Close the form within the webview first (for the css animation)
     */
    override fun dismissOverlay() {
        val currentActivity = Registry.lifecycleMonitor.currentActivity
        if (currentActivity is KlaviyoFormsOverlayActivity) {
            webViewClient.detachWebView(currentActivity)
            currentActivity.finish()
        }
    }
}

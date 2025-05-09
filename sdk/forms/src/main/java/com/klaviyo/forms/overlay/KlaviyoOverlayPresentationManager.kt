package com.klaviyo.forms.overlay

import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.forms.bridge.BridgeMessageHandler
import com.klaviyo.forms.bridge.KlaviyoBridgeMessageHandler
import com.klaviyo.forms.webview.KlaviyoWebViewClient
import com.klaviyo.forms.webview.KlaviyoWebViewManager

/**
 * Coordinates preloading klaviyo.js and presentation forms in an overlay activity
 */
internal class KlaviyoOverlayPresentationManager() : OverlayPresentationManager {

    private val bridgeMessageHandler: BridgeMessageHandler = KlaviyoBridgeMessageHandler(this).apply {
        Registry.register<BridgeMessageHandler>(this)
    }

    private val webViewManager: KlaviyoWebViewManager = KlaviyoWebViewClient(bridgeMessageHandler).apply {
        Registry.register<KlaviyoWebViewManager>(this)
    }

    /**
     * For verification that we receive the handshake data
     */
    private var handshakeTimer: Clock.Cancellable? = null

    /**
     * For tracking device rotation
     */
    private var orientation: Int? = null

    init {
        Registry.lifecycleMonitor.onActivityEvent(::onActivityEvent)
    }

    /**
     * Preload the webview and start the handshake timer
     * TODO add network monitor sensitivity
     */
    override fun preloadWebView() {
        webViewManager.initializeWebView()
        handshakeTimer?.cancel()
        handshakeTimer = Registry.clock.schedule(
            Registry.config.networkTimeout.toLong(),
            ::onPreloadTimeout
        )
    }

    /**
     * When the webview is loaded, we can cancel the timeout
     */
    override fun onPreloadComplete() {
        handshakeTimer?.cancel()
        handshakeTimer = null
    }

    /**
     * If the webview is not loaded in time, we cancel the handshake timer and destroy the webview
     * TODO - retrying preload with exponential backoff and network monitoring
     */
    private fun onPreloadTimeout() {
        handshakeTimer?.cancel()
        webViewManager.destroyWebView()
        Registry.log.debug("IAF WebView Aborted: Timeout waiting for Klaviyo.js")
    }

    /**
     * This closes the form on rotation, which we can detect with the local field
     * We wait for a change, see if it's different from the current, and close an open webview
     */
    private fun onActivityEvent(event: ActivityEvent) {
        if (event is ActivityEvent.ConfigurationChanged) {
            val newOrientation = event.newConfig.orientation
            if (orientation != newOrientation) {
                Registry.log.debug("New screen orientation, closing form")
                dismissOverlay() // TODO We can handle rotation better!
            }
            orientation = newOrientation
        }
    }

    /**
     * Launch the overlay activity
     * TODO never present a second overlay if already open
     */
    override fun presentOverlay() {
        Registry.config.applicationContext.startActivity(KlaviyoFormsOverlayActivity.launchIntent)
    }

    /**
     * TODO Close the form within the webview (to get the animation)
     * Detach the webview from the activity
     * Dismiss the activity
     */
    override fun dismissOverlay() {
        handshakeTimer?.cancel()

        val currentActivity = Registry.lifecycleMonitor.currentActivity
        if (currentActivity is KlaviyoFormsOverlayActivity) {
            webViewManager.detachWebView(currentActivity)
            currentActivity.finish()
        }
    }
}

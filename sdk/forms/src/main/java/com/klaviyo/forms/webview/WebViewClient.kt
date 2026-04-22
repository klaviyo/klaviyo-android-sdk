package com.klaviyo.forms.webview

import android.app.Activity
import android.view.View

internal interface WebViewClient {
    /**
     * Initialize a webview instance, with protection against duplication
     * and initialize klaviyo.js for In-App Forms with handshake data injected in the document head
     */
    fun initializeWebView()

    /**
     * Invoke when [com.klaviyo.forms.bridge.NativeBridgeMessage.HandShook] event is received: Local script is ready
     */
    fun onLocalJsReady()

    /**
     * Invoke when [com.klaviyo.forms.bridge.NativeBridgeMessage.HandShook] event is received: klaviyo.js has loaded
     */
    fun onJsHandshakeCompleted()

    /**
     * Get the underlying WebView instance, or null if not initialized
     */
    fun getWebView(): View?

    /**
     * Attach the webview to the overlay activity
     */
    fun attachWebView(activity: Activity): WebViewClient

    /**
     * Detach the webview from the overlay activity, keeping it in memory
     */
    fun detachWebView(): WebViewClient

    /**
     * Destroy the webview and release the reference
     */
    fun destroyWebView(): WebViewClient

    /**
     * Push a fresh [com.klaviyo.forms.bridge.DeviceInfo] snapshot into the webview's
     * `data-klaviyo-device` head attribute. Safe to call at any point after the webview
     * is initialized — no-ops if the webview is not ready.
     */
    fun pushDeviceInfo()
}

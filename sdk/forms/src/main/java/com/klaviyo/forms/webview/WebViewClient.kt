package com.klaviyo.forms.webview

import android.app.Activity

internal interface WebViewClient {
    /**
     * Initialize a webview instance, with protection against duplication
     * and initialize klaviyo.js for In-App Forms with handshake data injected in the document head
     */
    fun initializeWebView()

    /**
     * Invoke when [com.klaviyo.forms.bridge.NativeBridgeMessage.HandShook] event is received, klaviyo.js is loaded
     */
    fun onLocalJsReady()

    /**
     * Invoke when [com.klaviyo.forms.bridge.NativeBridgeMessage.HandShook] event is received, klaviyo.js is loaded
     */
    fun onJsHandshakeCompleted()

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
}

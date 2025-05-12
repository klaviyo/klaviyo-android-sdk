package com.klaviyo.forms.webview

import android.app.Activity
import com.klaviyo.forms.overlay.KlaviyoFormsOverlayActivity

internal interface WebViewClient {
    /**
     * Initialize a webview instance, with protection against duplication
     * and initialize klaviyo.js for in-app forms with handshake data injected in the document head
     */
    fun initializeWebView()

    /**
     * Invoke when [com.klaviyo.forms.bridge.BridgeMessage.HandShook] event is received, klaviyo.js is loaded
     */
    fun onJsHandshakeCompleted()

    /**
     * Attach the webview to the overlay activity
     */
    fun attachWebView(activity: KlaviyoFormsOverlayActivity): WebViewClient

    /**
     * Detach the webview from the overlay activity, keeping it in memory
     */
    fun detachWebView(activity: Activity): WebViewClient

    /**
     * Destroy the webview and release the reference
     */
    fun destroyWebView(): WebViewClient
}

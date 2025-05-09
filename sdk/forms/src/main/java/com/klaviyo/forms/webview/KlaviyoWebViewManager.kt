package com.klaviyo.forms.webview

import android.app.Activity
import com.klaviyo.forms.overlay.KlaviyoFormsOverlayActivity

internal interface KlaviyoWebViewManager {
    /**
     * Initialize a webview instance, with protection against duplication
     * and initialize klaviyo.js for in-app forms with handshake data injected in the document head
     */
    fun initializeWebView()

    /**
     * Attach the webview to the overlay activity
     */
    fun attachWebView(activity: KlaviyoFormsOverlayActivity): KlaviyoWebViewManager

    /**
     * Detach the webview from the overlay activity, keeping it in memory
     */
    fun detachWebView(activity: Activity): KlaviyoWebViewManager

    /**
     * Destroy the webview and release the reference
     */
    fun destroyWebView(): KlaviyoWebViewManager
}

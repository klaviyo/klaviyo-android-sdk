package com.klaviyo.forms.bridge

import android.webkit.JavascriptInterface
import androidx.webkit.WebViewCompat

interface BridgeMessageHandler : WebViewCompat.WebMessageListener {
    /**
     * This bridge object is injected into a [com.klaviyo.forms.webview.KlaviyoWebView] as a global property on the window.
     * This is the name that will be used to access the bridge from JS, i.e. window.KlaviyoNativeBridge
     */
    val name: String

    /**
     * The allowed origin for the webview content and bridge
     */
    val allowedOrigin: Set<String>

    @JavascriptInterface
    fun postMessage(message: String)
}
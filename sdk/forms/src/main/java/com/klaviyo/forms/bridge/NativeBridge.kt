package com.klaviyo.forms.bridge

import android.webkit.JavascriptInterface
import androidx.webkit.WebViewCompat

/**
 * An instance of this class is injected into a [com.klaviyo.forms.webview.KlaviyoWebView] as a global property
 * on the window. It receives and interprets messages from klaviyo.js over the native bridge
 */
internal interface NativeBridge : WebViewCompat.WebMessageListener {

    /**
     * This is the name that will be used to access the bridge from JS, i.e. window.KlaviyoNativeBridge
     */
    val name: String

    /**
     * The allowed origin for the webview content and bridge
     */
    val allowedOrigin: Set<String>

    /**
     * Handshake data indicating the message types/versions that the SDK supports receiving over the NativeBridge
     */
    val handshake: List<HandshakeSpec>

    /**
     * This method is invoked with klaviyo.js sends a message over the native bridge
     */
    @JavascriptInterface
    fun postMessage(message: String)
}

package com.klaviyo.messaging

import android.webkit.WebView
import com.klaviyo.core.Registry

class KlaviyoWebView {
    private val webView = WebView(Registry.config.applicationContext)

    fun loadUrl(url: String) {
        webView.loadUrl(url)
    }
}

package com.klaviyo.messaging

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import com.klaviyo.core.Registry

class KlaviyoWebView : WebViewClient() {
    companion object {
        private const val MIME_TYPE = "text/html"
    }

    private val webView = WebView(Registry.config.applicationContext).also {
        it.webViewClient = this
    }

    fun loadHtml(html: String) {
        webView.loadDataWithBaseURL(
            Registry.config.baseUrl,
            html,
            MIME_TYPE,
            null,
            Registry.config.baseUrl
        )
    }

    fun addTo(view: ViewGroup) {
        view.addView(webView)
    }
}

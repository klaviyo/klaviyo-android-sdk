package com.klaviyo.forms

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER
import androidx.webkit.WebViewFeature.isFeatureSupported
import com.klaviyo.core.DeviceProperties
import com.klaviyo.core.Registry

/**
 * View logic for in-app forms
 */
@SuppressLint("SetJavaScriptEnabled")
internal class KlaviyoWebView : WebView {
    constructor(
        context: Context = Registry.config.applicationContext
    ) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs, 0)

    fun loadTemplate(html: String, delegate: KlaviyoWebViewDelegate) = configure()
        .setDelegate(delegate)
        .loadDataWithBaseURL(
            delegate.allowedOrigin.first(),
            html,
            "text/html",
            null,
            null
        )

    private fun configure() = apply {
        settings.userAgentString = DeviceProperties.userAgent
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        setBackgroundColor(Color.TRANSPARENT)
        if (Registry.config.isDebugBuild) {
            setWebContentsDebuggingEnabled(true)
            // Disable webview resources cache when debugging:
            // settings.cacheMode = WebSettings.LOAD_NO_CACHE
        }
    }

    private fun setDelegate(delegate: KlaviyoWebViewDelegate) = apply {
        webViewClient = delegate

        if (isFeatureSupported(WEB_MESSAGE_LISTENER)) {
            Registry.log.verbose("$WEB_MESSAGE_LISTENER Supported")
            WebViewCompat.addWebMessageListener(
                this,
                delegate.bridgeName,
                delegate.allowedOrigin,
                delegate
            )
        } else {
            Registry.log.verbose("$WEB_MESSAGE_LISTENER Unsupported")
            addJavascriptInterface(delegate, delegate.bridgeName)
        }
    }
}

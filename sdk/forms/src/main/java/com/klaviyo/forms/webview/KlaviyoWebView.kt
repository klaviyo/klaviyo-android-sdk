package com.klaviyo.forms.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER
import androidx.webkit.WebViewFeature.isFeatureSupported
import com.klaviyo.core.DeviceProperties
import com.klaviyo.core.Registry
import com.klaviyo.forms.bridge.NativeBridge

/**
 * Custom WebView that powers the In-App Forms experience, running klaviyo.js in its JS engine
 * to handle forms behavior, triggering, rendering and ultimately displaying the form over the host app.
 */
@SuppressLint("SetJavaScriptEnabled")
internal class KlaviyoWebView : WebView {
    constructor(
        context: Context = Registry.config.applicationContext
    ) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs, 0)

    fun loadTemplate(html: String, client: WebViewClient, bridge: NativeBridge) = configure()
        .apply { webViewClient = client }
        .addBridge(bridge)
        .loadDataWithBaseURL(
            bridge.allowedOrigin.first(),
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

    /**
     * Inject native bridge message handler into the webview, uses feature detection to see
     * if we can use WebMessageListener or if we need to fall back on legacy JS interface
     */
    private fun addBridge(bridge: NativeBridge) = apply {
        if (isFeatureSupported(WEB_MESSAGE_LISTENER)) {
            Registry.log.verbose("$WEB_MESSAGE_LISTENER Supported")
            WebViewCompat.addWebMessageListener(
                this,
                bridge.name,
                bridge.allowedOrigin,
                bridge
            )
        } else {
            Registry.log.verbose("$WEB_MESSAGE_LISTENER Unsupported")
            addJavascriptInterface(bridge, bridge.name)
        }
    }
}

package com.klaviyo.messaging

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
import androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER
import androidx.webkit.WebViewFeature.isFeatureSupported
import com.klaviyo.analytics.DeviceProperties
import com.klaviyo.core.BuildConfig
import com.klaviyo.core.Registry
import com.klaviyo.messaging.KlaviyoWebViewClient.Companion.JS_BRIDGE_NAME
import com.klaviyo.messaging.KlaviyoWebViewClient.Companion.MIME_TYPE
import com.klaviyo.messaging.KlaviyoWebViewClient.Companion.USE_NEW_FEATURES
import com.klaviyo.messaging.KlaviyoWebViewClient.Companion.getBridgeJs

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
class KlaviyoWebView(activity: Activity) : Dialog(activity) {

    private val webViewClient: KlaviyoWebViewClient by lazy {
        KlaviyoWebViewClient(
            onShow = { show() },
            onClose = { dismiss() },
            evaluateJs = { script, result -> webView.evaluateJavascript(script, result) },
            launchWebViewUrl = { intent -> activity.startActivity(intent) }
        )
    }

    private var webView: WebView

    init {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        setContentView(R.layout.klaviyo_web_view)
        webView = findViewById(R.id.webview)
        webView.also {
            it.setBackgroundColor(Color.TRANSPARENT)
            it.webViewClient = webViewClient
            it.settings.userAgentString = DeviceProperties.userAgent
            setCanceledOnTouchOutside(true)
            it.settings.javaScriptEnabled = true

            if (USE_NEW_FEATURES && isFeatureSupported(WEB_MESSAGE_LISTENER)) {
                Registry.log.verbose("$WEB_MESSAGE_LISTENER Supported")
                WebViewCompat.addWebMessageListener(
                    it,
                    JS_BRIDGE_NAME,
                    setOf(Registry.config.baseUrl),
                    webViewClient
                )
            } else {
                Registry.log.verbose("$WEB_MESSAGE_LISTENER Unsupported")
                it.addJavascriptInterface(this, JS_BRIDGE_NAME)
            }

            if (USE_NEW_FEATURES && isFeatureSupported(DOCUMENT_START_SCRIPT)) {
                Registry.log.verbose("$DOCUMENT_START_SCRIPT Supported")
                WebViewCompat.addDocumentStartJavaScript(
                    it,
                    getBridgeJs(),
                    setOf(Registry.config.baseUrl)
                )
            } else {
                Registry.log.verbose("$DOCUMENT_START_SCRIPT Unsupported")
            }
        }
    }

    fun loadHtml(html: String) = webView.loadDataWithBaseURL(
        Registry.config.baseUrl,
        html,
        MIME_TYPE,
        null,
        null
    )
}

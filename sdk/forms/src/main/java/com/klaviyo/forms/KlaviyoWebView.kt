package com.klaviyo.forms

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER
import androidx.webkit.WebViewFeature.isFeatureSupported
import com.klaviyo.analytics.DeviceProperties
import com.klaviyo.core.BuildConfig
import com.klaviyo.core.Registry
import com.klaviyo.forms.KlaviyoWebViewDelegate.Companion.BRIDGE_NAME

@SuppressLint("SetJavaScriptEnabled")
internal class KlaviyoWebView : WebView {
    constructor(
        delegate: KlaviyoWebViewDelegate,
        context: Context = Registry.config.applicationContext
    ) : super(context) {
        settings.userAgentString = DeviceProperties.userAgent
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        setBackgroundColor(Color.TRANSPARENT)
        setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        setDelegate(delegate)
    }

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs, 0)

    private fun setDelegate(delegate: KlaviyoWebViewDelegate) = apply {
        webViewClient = delegate

        if (isFeatureSupported(WEB_MESSAGE_LISTENER)) {
            Registry.log.verbose("$WEB_MESSAGE_LISTENER Supported")
            WebViewCompat.addWebMessageListener(
                this,
                BRIDGE_NAME,
                delegate.allowedOrigin,
                delegate
            )
        } else {
            Registry.log.verbose("$WEB_MESSAGE_LISTENER Unsupported")
            addJavascriptInterface(delegate, BRIDGE_NAME)
        }
    }
}

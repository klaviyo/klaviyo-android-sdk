package com.klaviyo.messaging

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat.startActivity
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER
import androidx.webkit.WebViewFeature.isFeatureSupported
import com.klaviyo.analytics.DeviceProperties
import com.klaviyo.core.BuildConfig
import com.klaviyo.core.Registry
import org.json.JSONException
import org.json.JSONObject

class KlaviyoWebView : WebViewClient(), WebViewCompat.WebMessageListener {
    companion object {
        private const val MIME_TYPE = "text/html"
        private const val JS_BRIDGE_NAME = "Klaviyo"
    }

    init {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private val webView = WebView(Registry.config.applicationContext).also {
        it.setBackgroundColor(Color.TRANSPARENT)
        it.webViewClient = this
        it.settings.userAgentString = DeviceProperties.userAgent
        it.settings.javaScriptEnabled = true

        if (isFeatureSupported(WEB_MESSAGE_LISTENER)) {
            Registry.log.verbose("$WEB_MESSAGE_LISTENER Supported")
            WebViewCompat.addWebMessageListener(
                it,
                JS_BRIDGE_NAME,
                // TODO - sort out how to toggle between local and production.
                setOf("http://a.local-klaviyo.com:8080"),
                this
            )
        } else {
            Registry.log.verbose("$WEB_MESSAGE_LISTENER Unsupported")
            it.addJavascriptInterface(this, JS_BRIDGE_NAME)
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        Registry.log.error("HTTP Error: ${errorResponse?.statusCode} - ${request?.url}")
        super.onReceivedHttpError(view, request, errorResponse)
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        Registry.log.debug("Request: ${request?.url}")
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (request?.isForMainFrame == true) {
            Registry.log.debug("Overriding external URL to browser: ${request.url}")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = request.url
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(webView.context, intent, null)
            return true
        }
        return super.shouldOverrideUrlLoading(view, request)
    }

    fun addTo(view: ViewGroup) {
        webView.visibility = View.INVISIBLE
        view.addView(webView)
    }

    fun loadHtml(html: String) = webView.loadDataWithBaseURL(
        // TODO - sort out how to toggle between local and production.
        "http://a.local-klaviyo.com:8080",
        html,
        MIME_TYPE,
        null,
        null
    )

    fun show() = webView.post { webView.visibility = View.VISIBLE }

    fun close() = webView.post {
        webView.visibility = View.GONE
        webView.parent?.let {
            (it as ViewGroup).removeView(webView)
            webView.destroy()
        }
    }

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy
    ) {
        message.data?.let { data -> postMessage(data) }
    }

    @android.webkit.JavascriptInterface
    fun postMessage(message: String) {
        Registry.log.debug("JS interface postMessage $message")
        try {
            val jsonMessage = JSONObject(message)
            val jsonData = jsonMessage.optJSONObject("data") ?: JSONObject()

            when (jsonMessage.optString("type")) {
                "show" -> show()
                "close" -> close()
                "console" -> console(jsonData)
                else -> console(jsonData)
            }
        } catch (exception: JSONException) {
            Registry.log.warning("JS interface error", exception)
        }
    }

    private fun console(jsonData: JSONObject) {
        val message = jsonData.optJSONObject("message") ?: ""

        when (jsonData.optString("level")) {
            "log" -> Registry.log.info(message.toString())
            "warning" -> Registry.log.warning(message.toString())
            "error" -> Registry.log.error(message.toString())
        }
    }
}

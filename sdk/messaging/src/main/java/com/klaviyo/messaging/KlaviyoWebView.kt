package com.klaviyo.messaging

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Log
import java.io.BufferedReader
import org.json.JSONException
import org.json.JSONObject

class KlaviyoWebView : WebViewClient(), WebViewCompat.WebMessageListener {
    companion object {
        private const val MIME_TYPE = "text/html"
        private const val JS_BRIDGE_NAME = "Klaviyo"
        private const val USE_NEW_FEATURES = true

        fun getBridgeJs(): String = Registry.config.applicationContext
            .assets
            .open("bridge.js")
            .bufferedReader()
            .use(BufferedReader::readText)
            .apply {
                val opts = JSONObject()
                    .put("bridgeName", JS_BRIDGE_NAME)
                    .toString()

                return "$this('$opts');"
            }
    }

    init {
        Registry.log.logLevel = Log.Level.Verbose
        WebView.setWebContentsDebuggingEnabled(true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private val webView = WebView(Registry.config.applicationContext).also {
        it.webViewClient = this
        it.settings.javaScriptEnabled = true

        if (USE_NEW_FEATURES && WebViewFeature.isFeatureSupported(
                WebViewFeature.WEB_MESSAGE_LISTENER
            )
        ) {
            Registry.log.verbose("${WebViewFeature.WEB_MESSAGE_LISTENER} Supported")
            WebViewCompat.addWebMessageListener(
                it,
                JS_BRIDGE_NAME,
                setOf(Registry.config.baseUrl),
                this
            )
        } else {
            Registry.log.verbose("${WebViewFeature.WEB_MESSAGE_LISTENER} Unsupported")
            it.addJavascriptInterface(this, JS_BRIDGE_NAME)
        }

        if (USE_NEW_FEATURES && WebViewFeature.isFeatureSupported(
                WebViewFeature.DOCUMENT_START_SCRIPT
            )
        ) {
            Registry.log.verbose("${WebViewFeature.DOCUMENT_START_SCRIPT} Supported")
            WebViewCompat.addDocumentStartJavaScript(
                it,
                getBridgeJs(),
                setOf(Registry.config.baseUrl)
            )
        } else {
            Registry.log.verbose("${WebViewFeature.DOCUMENT_START_SCRIPT} Unsupported")
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)

        if (!USE_NEW_FEATURES || WebViewFeature.isFeatureSupported(
                WebViewFeature.DOCUMENT_START_SCRIPT
            )
        ) {
            webView.evaluateJavascript(getBridgeJs(), null)
        }
    }

    fun addTo(view: ViewGroup) {
        webView.visibility = View.INVISIBLE
        view.addView(webView)
    }

    fun loadHtml(html: String) {
        webView.loadDataWithBaseURL(
            Registry.config.baseUrl,
            html,
            MIME_TYPE,
            null,
            null
        )
    }

    fun show() {
        webView.post { webView.visibility = View.VISIBLE }
    }

    fun close() {
        webView.post { webView.visibility = View.GONE }
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
        try {
            val jsonMessage = JSONObject(message)
            val jsonData = jsonMessage.optJSONObject("data") ?: JSONObject()

            when (jsonMessage.optString("type")) {
                "imagesLoaded" -> show()
                "close" -> close()
                "console" -> console(jsonData)
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

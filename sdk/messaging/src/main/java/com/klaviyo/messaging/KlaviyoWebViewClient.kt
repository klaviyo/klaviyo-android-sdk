package com.klaviyo.messaging

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
import androidx.webkit.WebViewFeature.isFeatureSupported
import com.klaviyo.core.Registry
import java.io.BufferedReader
import org.json.JSONException
import org.json.JSONObject

class KlaviyoWebViewClient(
    private val onShow: () -> Unit,
    private val onClose: () -> Unit,
    private val evaluateJs: (String, (String) -> Unit) -> Unit,
    private val launchWebViewUrl: (Intent) -> Unit
) : WebViewClient(), WebViewCompat.WebMessageListener {

    companion object {
        internal const val MIME_TYPE = "text/html"
        internal const val JS_BRIDGE_NAME = "Klaviyo"
        internal const val USE_NEW_FEATURES = true

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

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)

        if (!USE_NEW_FEATURES || isFeatureSupported(DOCUMENT_START_SCRIPT)) {
            // When addDocumentStartJavaScript is not supported, we have to inject our JS onPageStarted
            evaluateJs(getBridgeJs()) {}
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
            launchWebViewUrl(intent)
            return true
        }
        return super.shouldOverrideUrlLoading(view, request)
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
            Registry.log.debug("JS interface postMessage $jsonData")

            when (jsonMessage.optString("type")) {
                "documentReady" -> onShow() // todo get a more accurate way to tell if webview loaded
                "close" -> onClose() // todo get a more accurate way to tell if close was pressed
                "console" -> console(jsonData)
                else -> console(jsonData)
            }
        } catch (exception: JSONException) {
            Registry.log.warning("JS interface error", exception)
        }
    }

    private fun console(jsonData: JSONObject) {
        val message = jsonData.optString("message") ?: ""

        when (jsonData.optString("level")) {
            "log" -> Registry.log.info(message)
            "warning" -> Registry.log.warning(message)
            "error" -> Registry.log.error(message)
        }
    }
}

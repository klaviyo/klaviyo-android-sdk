package com.klaviyo.messaging

import android.annotation.SuppressLint
import android.app.Activity
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
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.BuildConfig
import com.klaviyo.core.Registry
import java.lang.ref.WeakReference

class KlaviyoWebView(
    activity: Activity
) : WebViewClient(), WebViewCompat.WebMessageListener {
    companion object {
        private const val MIME_TYPE = "text/html"
    }

    private val activityRef = WeakReference(activity)

    init {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private val webView = WebView(activity).also {
        it.setBackgroundColor(Color.TRANSPARENT)
        it.webViewClient = this
        it.settings.userAgentString = DeviceProperties.userAgent
        it.settings.javaScriptEnabled = true

        if (isFeatureSupported(WEB_MESSAGE_LISTENER)) {
            Registry.log.verbose("$WEB_MESSAGE_LISTENER Supported")
            WebViewCompat.addWebMessageListener(
                it,
                IAF_BRIDGE_NAME,
                // TODO - sort out how to toggle between local and production.
                setOf("http://a.local-klaviyo.com:8080"),
                this
            )
        } else {
            Registry.log.verbose("$WEB_MESSAGE_LISTENER Unsupported")
            it.addJavascriptInterface(this, IAF_BRIDGE_NAME)
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
            activityRef.get()?.let { startActivity(it, intent, null) }
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

    private fun show() = webView.post { webView.visibility = View.VISIBLE }

    private fun close() = webView.post {
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
            when (val messageType = decodeWebviewMessage(message)) {
                KlaviyoWebFormMessageType.Close -> close()
                is KlaviyoWebFormMessageType.ProfileEvent -> Klaviyo.createEvent(messageType.event)
                KlaviyoWebFormMessageType.Show -> show()
                is KlaviyoWebFormMessageType.AggregateEventTracked -> Registry.get<ApiClient>()
                    .enqueueAggregateEvent(messageType.payload)
                is KlaviyoWebFormMessageType.DeepLink -> deepLink(messageType)
            }
        } catch (e: Exception) {
            Registry.log.error("Failed to relay webview message: $message", e)
        }
    }

    private fun deepLink(messageType: KlaviyoWebFormMessageType.DeepLink) {
        val uri = Uri.parse(messageType.route)
        activityRef.get()?.let { activity ->
            activity.startActivity(
                Intent().apply {
                    data = uri
                    action = Intent.ACTION_VIEW
                    setPackage(activity.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
        } ?: run {
            Registry.log.error(
                "Failed to launch deeplink ${messageType.route}, activity reference null"
            )
        }
    }
}

package com.klaviyo.forms

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
import com.klaviyo.core.config.Clock
import com.klaviyo.core.utils.WeakReferenceDelegate

internal class KlaviyoWebViewDelegate : WebViewClient(), WebViewCompat.WebMessageListener {
    companion object {
        private const val MIME_TYPE = "text/html"
    }

    private val activity: Activity?
        get() = Registry.lifecycleMonitor.currentActivity

    // for timeout on js communications
    private var timer: Clock.Cancellable? = null

    private var webView: WebView? by WeakReferenceDelegate()

    init {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView = WebView(Registry.config.applicationContext).also {
        it.setBackgroundColor(Color.TRANSPARENT)
        it.webViewClient = this
        it.settings.userAgentString = DeviceProperties.userAgent
        it.settings.javaScriptEnabled = true
        it.settings.domStorageEnabled = true
        if (isFeatureSupported(WEB_MESSAGE_LISTENER)) {
            Registry.log.verbose("$WEB_MESSAGE_LISTENER Supported")
            WebViewCompat.addWebMessageListener(
                it,
                IAF_BRIDGE_NAME,
                setOf(Registry.config.baseCdnUrl),
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
            activity?.let {
                startActivity(it, intent, null)
            } ?: run {
                Registry.log.error("Unable to launch external browser, null activity reference")
            }
            return true
        }
        return super.shouldOverrideUrlLoading(view, request)
    }

    fun loadHtml(html: String) = webView?.let {
        Registry.log.debug("Not loading into $it since its active")
    } ?: run {
        webView = buildWebView()
        Registry.log.wtf("Loading html into $webView")
        webView?.loadDataWithBaseURL(
            Registry.config.baseCdnUrl,
            html,
            MIME_TYPE,
            null,
            null
        )
        timer?.cancel()
        timer = Registry.clock.schedule(Registry.config.networkTimeout.toLong()) {
            Registry.log.debug("IAF WebView Aborted: Timeout waiting for Klaviyo.js")
            close()
        }
    }

    private fun handShook() {
        Registry.log.info("Received message from JS bridge")
        timer?.cancel()
    }

    private fun show() = webView?.let { webView ->
        activity?.let {
            it.window.decorView
                .findViewById<ViewGroup>(android.R.id.content)
                .addView(webView)
            webView.post { webView.visibility = View.VISIBLE }
        } ?: run {
            Registry.log.error("Unable to show IAF - null activity context reference")
        }
    }

    private fun close() = webView?.let { webView ->
        webView.post {
            webView.visibility = View.GONE
            webView.parent?.let {
                (it as ViewGroup).removeView(webView)
                webView.destroy()
            }
            this.webView = null
            Registry.log.debug("IAF WebView: clearing internal webview reference")
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
                KlaviyoWebFormMessageType.HandShook -> handShook()
            }
        } catch (e: Exception) {
            Registry.log.error("Failed to relay webview message: $message", e)
        }
    }

    private fun deepLink(messageType: KlaviyoWebFormMessageType.DeepLink) {
        val uri = Uri.parse(messageType.route)
        activity?.let { activity ->
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

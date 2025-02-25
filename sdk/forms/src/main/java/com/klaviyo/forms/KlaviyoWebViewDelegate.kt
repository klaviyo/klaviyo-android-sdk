package com.klaviyo.forms

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
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
        private val wildcard = setOf("*")

        private val templateHtml = Uri.parse("file:///android_asset/InAppFormsTemplate.html")

        private val klaviyoJsUrl: Uri
            get() = Uri.parse(Registry.config.baseCdnUrl)
                .buildUpon()
                .path("onsite/js/klaviyo.js")
                .appendQueryParameter("company_id", Registry.config.apiKey)
                .appendQueryParameter("env", "in-app")
                .build()

        private val loadScript: String
            get() = """
                if (document.head) {
                    // Page load has already started, initialize now
                    initialize();
                } else {
                    // Page load has not started, postpone initialize
                    document.addEventListener('DOMContentLoaded', initialize);
                }
                
                function initialize() {
                    const script = document.createElement('script');
                    script.src = '$klaviyoJsUrl';
                    script.type = 'text/javascript';
                    script.async = false;
                    
                    document.head.appendChild(script);
                    document.head.setAttribute("data-sdk-name", "${Registry.config.sdkName}");
                    document.head.setAttribute("data-sdk-version", "${Registry.config.sdkVersion}");
                    document.head.setAttribute("data-native-bridge-name", "$IAF_BRIDGE_NAME");
                    document.head.setAttribute("data-native-bridge-handshake", '$IAF_HANDSHAKE');
                }
            """.trimIndent()
    }

    private val activity: Activity?
        get() = Registry.lifecycleMonitor.currentActivity

    /**
     * For timeout on js communications
     */
    private var timer: Clock.Cancellable? = null

    private var webView: WebView? by WeakReferenceDelegate()

    init {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView = WebView(Registry.config.applicationContext).also {
        it.webViewClient = this
        it.settings.userAgentString = DeviceProperties.userAgent
        it.settings.javaScriptEnabled = true
        it.settings.domStorageEnabled = true
        it.setBackgroundColor(Color.TRANSPARENT)

        if (isFeatureSupported(WEB_MESSAGE_LISTENER)) {
            Registry.log.verbose("$WEB_MESSAGE_LISTENER Supported")
            WebViewCompat.addWebMessageListener(
                it,
                IAF_BRIDGE_NAME,
                wildcard,
                this
            )
        } else {
            Registry.log.verbose("$WEB_MESSAGE_LISTENER Unsupported")
            it.addJavascriptInterface(this, IAF_BRIDGE_NAME)
        }
    }

    /**
     * Initialize a webview instance, with protection against duplication
     * and initialize  klaviyo.js for in-app forms with handshake data injected in the document head
     */
    fun initializeWebView() = webView?.let {
        Registry.log.debug("")
    } ?: buildWebView().also { webView ->
        this.webView = webView

        if (isFeatureSupported(DOCUMENT_START_SCRIPT)) {
            // This feature automatically adds your script right before document starts to load
            Registry.log.verbose("$DOCUMENT_START_SCRIPT Supported")
            WebViewCompat.addDocumentStartJavaScript(webView, loadScript, wildcard)
        }

        webView.loadUrl(templateHtml.toString())

        timer?.cancel()
        timer = Registry.clock.schedule(Registry.config.networkTimeout.toLong()) {
            Registry.log.debug("IAF WebView Aborted: Timeout waiting for Klaviyo.js")
            close()
        }
    }

    /**
     * When [DOCUMENT_START_SCRIPT] is unsupported, we must inject our script [onPageStarted] instead
     */
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = view
        ?.takeUnless { isFeatureSupported(DOCUMENT_START_SCRIPT) }
        ?.let { webView ->
            Registry.log.verbose("$DOCUMENT_START_SCRIPT Unsupported")
            webView.evaluateJavascript(loadScript) { }
        } ?: Unit

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

    /**
     * Intercept page navigation and redirect to an external browser application
     */
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
                Registry.log.error("Unable to launch external browser - null activity reference")
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
        activity?.window?.decorView?.let { decorView ->
            decorView.post {
                decorView.findViewById<ViewGroup>(android.R.id.content).addView(webView)
                webView.visibility = View.VISIBLE
            }
        } ?: run {
            Registry.log.error("Unable to show IAF - null activity context reference")
        }
    } ?: run {
        Registry.log.error("Unable to show IAF - null WebView reference")
    }

    private fun close() = webView?.let { webView ->
        activity?.window?.decorView?.let { decorView ->
            decorView.post {
                Registry.log.debug("IAF WebView: clearing internal webview reference")
                this.webView = null
                webView.visibility = View.GONE
                webView.parent?.let {
                    (it as ViewGroup).removeView(webView)
                    webView.destroy()
                }
            }
        } ?: run {
            Registry.log.error("Unable to close IAF - null activity context reference")
        }
    } ?: run {
        Registry.log.error("Unable to close IAF - null WebView reference")
    }

    /**
     * When [WEB_MESSAGE_LISTENER] is supported, messages sent over the Native Bridge from JS are received here
     */
    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy
    ) = message.data?.let { postMessage(it) } ?: Unit

    /**
     * When [WEB_MESSAGE_LISTENER] is NOT supported, messages sent over the Native Bridge from JS are received here
     */
    @android.webkit.JavascriptInterface
    fun postMessage(message: String) {
        Registry.log.debug("JS interface postMessage $message")
        try {
            when (val messageType = decodeWebviewMessage(message)) {
                KlaviyoWebFormMessageType.HandShook -> handShook()
                KlaviyoWebFormMessageType.Show -> show()
                is KlaviyoWebFormMessageType.AggregateEventTracked -> createAggregateEvent(
                    messageType
                )
                is KlaviyoWebFormMessageType.ProfileEvent -> createProfileEvent(messageType)
                is KlaviyoWebFormMessageType.DeepLink -> deepLink(messageType)
                KlaviyoWebFormMessageType.Close -> close()
            }
        } catch (e: Exception) {
            Registry.log.error("Failed to relay webview message: $message", e)
        }
    }

    private fun handShook() = timer?.cancel()

    private fun show() = webView?.let { webView ->
        activity?.let {
            it.window.decorView.post {
                it.window.decorView
                    .findViewById<ViewGroup>(android.R.id.content)
                    .addView(webView)
                    .also { webView.visibility = View.VISIBLE }
            }
        } ?: run {
            Registry.log.error("Unable to show IAF - null activity reference")
        }
    } ?: run {
        Registry.log.error("Unable to show IAF - WebView garbage collected")
    }

    private fun createAggregateEvent(message: KlaviyoWebFormMessageType.AggregateEventTracked) =
        Registry.get<ApiClient>().enqueueAggregateEvent(message.payload)

    private fun createProfileEvent(message: KlaviyoWebFormMessageType.ProfileEvent) =
        Klaviyo.createEvent(message.event)

    private fun deepLink(messageType: KlaviyoWebFormMessageType.DeepLink) = activity?.let { activity ->
        activity.startActivity(
            Intent().apply {
                data = Uri.parse(messageType.route)
                action = Intent.ACTION_VIEW
                setPackage(activity.packageName)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    } ?: run {
        Registry.log.error(
            "Failed to launch deeplink ${messageType.route} - null activity reference"
        )
    }

    private fun close() = webView?.let { webView ->
        webView.post {
            webView.visibility = View.GONE
            webView.parent?.let {
                (it as ViewGroup).removeView(webView)
                webView.destroy()
            }
            this.webView = null
        }
    }
}

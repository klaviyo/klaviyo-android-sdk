package com.klaviyo.forms

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
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
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.utils.WeakReferenceDelegate
import com.klaviyo.forms.BridgeMessage.Companion.handShakeData
import java.io.BufferedReader

internal class KlaviyoWebViewDelegate : WebViewClient(), WebViewCompat.WebMessageListener {
    companion object {
        internal const val BRIDGE_NAME = "KlaviyoNativeBridge"

        private const val MIME_TYPE = "text/html"

        private const val LOAD_FROM_FILE = true

        private val templateUrl = Uri.parse("file:///android_asset/InAppFormsTemplate.html")

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
                    document.head.setAttribute("data-native-bridge-name", "$BRIDGE_NAME");
                    document.head.setAttribute("data-native-bridge-handshake", '$handShakeData');
                }
            """.trimIndent()
    }

    /**
     * Defines origin(s) for which this delegate should be used
     */
    val allowedOrigin = if (LOAD_FROM_FILE) setOf("*") else setOf(Registry.config.baseUrl)

    private val activity: Activity? get() = Registry.lifecycleMonitor.currentActivity

    /**
     * For timeout on native bridge "handshake" event
     */
    private var handshakeTimer: Clock.Cancellable? = null

    /**
     * Weak reference to the WebView to avoid memory leak
     */
    private var webView: WebView? by WeakReferenceDelegate()

    /**
     * Initialize a webview instance, with protection against duplication
     * and initialize klaviyo.js for in-app forms with handshake data injected in the document head
     */
    fun initializeWebView() = webView ?: KlaviyoWebView(this).also { webView ->
        this.webView = webView

        if (LOAD_FROM_FILE && isFeatureSupported(DOCUMENT_START_SCRIPT)) {
            // This feature automatically adds your script right before document starts to load
            Registry.log.verbose("$DOCUMENT_START_SCRIPT Supported")
            WebViewCompat.addDocumentStartJavaScript(webView, loadScript, allowedOrigin)
        }

        if (LOAD_FROM_FILE) {
            webView.loadUrl(templateUrl.toString())
        } else {
            val html = Registry.config.applicationContext
                .assets
                .open("InAppFormsTemplate.html")
                .bufferedReader()
                .use(BufferedReader::readText)
                .replace("SDK_NAME", Registry.config.sdkName)
                .replace("SDK_VERSION", Registry.config.sdkVersion)
                .replace("BRIDGE_NAME", BRIDGE_NAME)
                .replace("BRIDGE_HANDSHAKE", handShakeData)
                .replace("KLAVIYO_JS_URL", klaviyoJsUrl.toString())

            webView.loadDataWithBaseURL(Registry.config.baseUrl, html, MIME_TYPE, null, null)
        }

        handshakeTimer?.cancel()
        handshakeTimer = Registry.clock.schedule(Registry.config.networkTimeout.toLong()) {
            Registry.log.debug("IAF WebView Aborted: Timeout waiting for Klaviyo.js")
            close()
        }
    }

    /**
     * When [DOCUMENT_START_SCRIPT] is unsupported, we must inject our script [onPageStarted] instead
     */
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = view
        ?.takeUnless { !LOAD_FROM_FILE || isFeatureSupported(DOCUMENT_START_SCRIPT) }
        ?.let { webView ->
            Registry.log.verbose("$DOCUMENT_START_SCRIPT Unsupported")
            webView.evaluateJavascript(loadScript) { }
        } ?: Unit

    /**
     * Called when loading a resource encounters http status code >= 400
     */
    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        Registry.log.warning("HTTP Error: ${errorResponse?.statusCode} - ${request?.url}")
        super.onReceivedHttpError(view, request, errorResponse)
    }

    /**
     * Intercept page navigation and redirect to an external browser application
     */
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (request?.isForMainFrame == true) {
            Registry.log.info("Redirect URL to external browser: ${request.url}")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = request.url
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity?.let {
                startActivity(it, intent, null)
            } ?: run {
                Registry.log.warning("Unable to launch external browser - null activity reference")
            }
            return true
        }
        return super.shouldOverrideUrlLoading(view, request)
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
    fun postMessage(message: String) = try {
        Registry.log.debug("JS interface postMessage $message")
        when (val bridgeMessage = BridgeMessage.decodeWebviewMessage(message)) {
            BridgeMessage.HandShook -> handShook()
            BridgeMessage.Show -> show()
            is BridgeMessage.AggregateEventTracked -> createAggregateEvent(bridgeMessage)
            is BridgeMessage.ProfileEvent -> createProfileEvent(bridgeMessage)
            is BridgeMessage.DeepLink -> deepLink(bridgeMessage)
            BridgeMessage.Close -> close()
            is BridgeMessage.Abort -> abort(bridgeMessage.reason)
        }
    } catch (e: Exception) {
        Registry.log.warning("Failed to relay webview message: $message", e)
    }.let { }

    /**
     * Handle a [BridgeMessage.HandShook] message by clearing the timer
     */
    private fun handShook() = handshakeTimer?.cancel()

    /**
     * Handle a [BridgeMessage.Show] message by displaying the webview before the form animates in
     */
    private fun show() = webView?.let { webView ->
        activity?.window?.decorView?.let { decorView ->
            decorView.post {
                decorView.findViewById<ViewGroup>(android.R.id.content).addView(webView)
                webView.visibility = View.VISIBLE
            }
        } ?: run {
            Registry.log.warning("Unable to show IAF - null activity reference")
        }
    } ?: run {
        Registry.log.warning("Unable to show IAF - null WebView reference")
    }

    /**
     * Handle a [BridgeMessage.AggregateEventTracked] message by creating an API call
     */
    private fun createAggregateEvent(message: BridgeMessage.AggregateEventTracked) =
        Registry.get<ApiClient>().enqueueAggregateEvent(message.payload)

    /**
     * Handle a [BridgeMessage.ProfileEvent] message by creating an API call
     */
    private fun createProfileEvent(message: BridgeMessage.ProfileEvent) =
        Klaviyo.createEvent(message.event)

    /**
     * Handle a [BridgeMessage.DeepLink] message by broadcasting an intent to the host app
     * similar to how we handle deep links from a notification
     */
    private fun deepLink(messageType: BridgeMessage.DeepLink) = activity?.let { activity ->
        activity.startActivity(
            Intent().apply {
                data = Uri.parse(messageType.route)
                action = Intent.ACTION_VIEW
                setPackage(activity.packageName)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    } ?: Registry.log.warning(
        "Failed to launch deeplink ${messageType.route} - null activity reference"
    )

    /**
     * Handle a [BridgeMessage.Close] message by detaching and destroying the [KlaviyoWebView]
     */
    private fun close() = webView?.let { webView ->
        activity?.window?.decorView?.let { decorView ->
            decorView.post {
                Registry.log.verbose("Clear IAF WebView reference")
                this.webView = null
                webView.visibility = View.GONE
                webView.parent?.let {
                    (it as ViewGroup).removeView(webView)
                    webView.destroy()
                }
            }
        } ?: run {
            Registry.log.warning("Unable to close IAF - null activity reference")
        }
    } ?: run {
        Registry.log.warning("Unable to close IAF - null WebView reference")
    }

    /**
     * Handle a [BridgeMessage.Abort] message by logging the reason and destroying the webview
     */
    private fun abort(reason: String) = Registry.log.info("IAF aborted, reason: $reason").also {
        close()
    }
}

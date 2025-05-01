package com.klaviyo.forms

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.utils.WeakReferenceDelegate
import com.klaviyo.forms.BridgeMessage.Companion.handShakeData
import java.io.BufferedReader

/**
 * Manages [KlaviyoWebView] to power in-app forms
 */
internal class KlaviyoWebViewDelegate : WebViewClient(), WebViewCompat.WebMessageListener {
    /**
     * Defines origin(s) for which this delegate should be used
     */
    val allowedOrigin: Set<String> get() = setOf(Registry.config.baseUrl)

    val bridgeName = "KlaviyoNativeBridge"

    /**
     * For timeout on native bridge "handshake" event
     */
    private var handshakeTimer: Clock.Cancellable? = null

    /**
     * Weak reference to the WebView to avoid memory leak
     */
    private var webView: KlaviyoWebView? by WeakReferenceDelegate()

    /**
     * For tracking device rotation
     */
    private var orientation: Int? = null

    init {
        /**
         * This closes the form on rotation, which we can detect with the local field
         * We wait for a change, see if it's different from the current, and close an open webview
         */
        Registry.lifecycleMonitor.onActivityEvent {
            if (it is ActivityEvent.ConfigurationChanged) {
                val newOrientation = it.newConfig.orientation
                if (orientation != newOrientation) {
                    Registry.log.debug("New screen orientation, closing form")
                    close()
                }
                orientation = newOrientation
            }
        }
    }

    /**
     * Initialize a webview instance, with protection against duplication
     * and initialize klaviyo.js for in-app forms with handshake data injected in the document head
     */
    fun initializeWebView() = webView?.apply {
        Registry.log.debug("Klaviyo webview is already initialized")
    } ?: KlaviyoWebView().also { webView ->
        this.webView = webView

        val klaviyoJsUrl = Uri.parse(Registry.config.baseCdnUrl)
            .buildUpon()
            .path("onsite/js/klaviyo.js")
            .appendQueryParameter("company_id", Registry.config.apiKey)
            .appendQueryParameter("env", "in-app")
            .appendAssetSource()
            .build()

        Registry.config.applicationContext.assets
            .open("InAppFormsTemplate.html")
            .bufferedReader()
            .use(BufferedReader::readText)
            .replace("SDK_NAME", Registry.config.sdkName)
            .replace("SDK_VERSION", Registry.config.sdkVersion)
            .replace("BRIDGE_NAME", this.bridgeName)
            .replace("BRIDGE_HANDSHAKE", handShakeData)
            .replace("KLAVIYO_JS_URL", klaviyoJsUrl.toString())
            .replace("FORMS_ENVIRONMENT", Registry.config.formEnvironment.templateName)
            .also { html ->
                webView.loadTemplate(html, this)
            }

        handshakeTimer?.cancel()
        handshakeTimer = Registry.clock.schedule(Registry.config.networkTimeout.toLong()) {
            Registry.log.debug("IAF WebView Aborted: Timeout waiting for Klaviyo.js")
            close()
        }
    }

    private fun Uri.Builder.appendAssetSource() = Registry.config.assetSource?.let { assetSource ->
        Registry.log.debug("Appending assetSource=$assetSource to klaviyo.js")
        appendQueryParameter("assetSource", assetSource)
    } ?: this

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

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // When an assetSource is specified, log whether we actually got it
        Registry.config.assetSource?.let { expected ->
            webView?.evaluateJavascript("window.klaviyoModulesObject?.assetSource") { actual ->
                Registry.log.debug("Actual Asset Source: $actual. Expected $expected")
            }
        }
    }

    /**
     * If the webview renderer crashes or gets cleaned up to reclaim memory
     * we have to clean up and return true here else the host app will crash
     */
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean = close().let {
        Registry.log.error("WebView crashed or deallocated")
        return true
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
            Registry.config.applicationContext.startActivity(intent, null)
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
            is BridgeMessage.Show -> show()
            is BridgeMessage.AggregateEventTracked -> createAggregateEvent(bridgeMessage)
            is BridgeMessage.ProfileEvent -> createProfileEvent(bridgeMessage)
            is BridgeMessage.DeepLink -> deepLink(bridgeMessage)
            is BridgeMessage.Close -> close()
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
     * Handle a [BridgeMessage.Show] message by launching the overlay activity
     */
    private fun show() {
        Registry.config.applicationContext.startActivity(KlaviyoFormsOverlayActivity.launchIntent)
    }

    /**
     * Attach the webview to the overlay activity
     * (Naive PoC implementation, probably want to consider a more robust architecture?)
     */
    fun attachWebView(activity: KlaviyoFormsOverlayActivity) = webView?.let { webView ->
        activity.setContentView(webView)
        webView.visibility = View.VISIBLE
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
     * Handle a [BridgeMessage.DeepLink] message by broadcasting an [Intent] to the host app
     * similar to how we handle deep links from a notification
     */
    private fun deepLink(messageType: BridgeMessage.DeepLink) {
        Registry.config.applicationContext.startActivity(
            Intent().apply {
                data = Uri.parse(messageType.route)
                action = Intent.ACTION_VIEW
                setPackage(Registry.config.applicationContext.packageName)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    /**
     * Handle a [BridgeMessage.Close] message by detaching and destroying the [KlaviyoWebView]
     */
    private fun close() = webView?.let { webView ->
        handshakeTimer?.cancel()
        val currentActivity = Registry.lifecycleMonitor.currentActivity

        if (currentActivity is KlaviyoFormsOverlayActivity) {
            currentActivity.finish()
        } else if (currentActivity != null) {
            detachWebView(currentActivity)
        }
    } ?: run {
        Registry.log.warning("Unable to close IAF - null WebView reference")
    }

    fun detachWebView(activity: Activity) = webView?.let { webView ->
        activity.runOnUiThread {
            Registry.log.verbose("Clear IAF WebView reference")
            this.webView = null
            webView.visibility = View.GONE
            webView.parent?.let {
                it as ViewGroup
            }?.removeView(webView)
            webView.destroy()
        }
    } ?: run {
        Registry.log.warning("Unable to detach IAF - null WebView reference")
    }

    /**
     * Handle a [BridgeMessage.Abort] message by logging the reason and destroying the webview
     */
    private fun abort(reason: String) = close().also {
        Registry.log.info("IAF aborted, reason: $reason")
    }

    /**
     * View.post but with self as an argument
     */
    private fun View.post(fn: (View) -> Unit) = apply { post { fn(this) } }
}

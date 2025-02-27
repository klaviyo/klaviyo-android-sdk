package com.klaviyo.forms

import android.app.Activity
import android.content.Intent
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
    val allowedOrigin = setOf(Registry.config.baseUrl)

    val bridgeName = "KlaviyoNativeBridge"

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
    fun initializeWebView() = webView ?: KlaviyoWebView().also { webView ->
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
            .also { html ->
                webView.loadTemplate(html, this)
            }

        handshakeTimer?.cancel()
        handshakeTimer = Registry.clock.schedule(Registry.config.networkTimeout.toLong()) {
            Registry.log.debug("IAF WebView Aborted: Timeout waiting for Klaviyo.js")
            close()
        }
    }

    private fun Uri.Builder.appendAssetSource() = apply {
        if (Registry.config.assetSource.isNullOrEmpty()) {
            Registry.log.info("Appending assetSource=${Registry.config.assetSource} to klaviyo.js")
            appendQueryParameter("assetSource", Registry.config.assetSource)
        }
    }

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

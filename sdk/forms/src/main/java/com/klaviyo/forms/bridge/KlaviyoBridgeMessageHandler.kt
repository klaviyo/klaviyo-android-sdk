package com.klaviyo.forms.bridge

import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.Registry
import com.klaviyo.forms.overlay.OverlayPresentationManager
import com.klaviyo.forms.webview.WebViewClient

/**
 * An instance of this class is injected into a [com.klaviyo.forms.webview.KlaviyoWebView] as a global property
 * on the window. It receives and interprets messages from klaviyo.js over the native bridge
 */
internal class KlaviyoBridgeMessageHandler(
    private val presentationManager: OverlayPresentationManager = Registry.get()
) : BridgeMessageHandler {

    /**
     * This is the name that will be used to access the bridge from JS, i.e. window.KlaviyoNativeBridge
     */
    override val name = "KlaviyoNativeBridge"

    /**
     * The allowed origin for the webview content and bridge
     */
    override val allowedOrigin: Set<String> get() = setOf(Registry.config.baseUrl)

    /**
     * When [WEB_MESSAGE_LISTENER] is supported, messages sent over the Native Bridge from JS are received here
     */
    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy
    ) = message.data?.let { postMessage(it) } ?: run {
        Registry.log.warning("Received null message from webview")
    }

    /**
     * When [WEB_MESSAGE_LISTENER] is NOT supported, messages sent over the Native Bridge from JS are received here
     */
    @JavascriptInterface
    override fun postMessage(message: String) {
        try {
            Registry.log.debug("JS interface postMessage $message")
            when (val bridgeMessage = BridgeMessage.decodeWebviewMessage(message)) {
                BridgeMessage.HandShook -> handShook()
                is BridgeMessage.Show -> show(bridgeMessage)
                is BridgeMessage.AggregateEventTracked -> createAggregateEvent(bridgeMessage)
                is BridgeMessage.ProfileEvent -> createProfileEvent(bridgeMessage)
                is BridgeMessage.DeepLink -> deepLink(bridgeMessage)
                is BridgeMessage.Close -> close()
                is BridgeMessage.Abort -> abort(bridgeMessage.reason)
            }
        } catch (e: Exception) {
            Registry.log.error("Failed to relay webview message: $message", e)
        }
    }

    /**
     * Notify the client that the handshake has completed
     */
    private fun handShook() = Registry.get<WebViewClient>().onJsHandshakeCompleted()

    /**
     * Notify the client that the webview should be shown
     */
    private fun show(bridgeMessage: BridgeMessage.Show) = presentationManager.presentOverlay().also {
        Registry.log.debug("Present form ${bridgeMessage.formId}")
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
    private fun deepLink(messageType: BridgeMessage.DeepLink) {
        Registry.lifecycleMonitor.currentActivity?.startActivity(
            Intent().apply {
                data = messageType.route.toUri()
                action = Intent.ACTION_VIEW
                `package` = Registry.config.applicationContext.packageName
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        ) ?: run {
            Registry.log.error("Unable to open deep link - null activity reference")
        }
    }

    /**
     * Instruct presentation manager to dismiss the form overlay activity
     */
    private fun close() = presentationManager.dismissOverlay()

    /**
     * Handle a [BridgeMessage.Abort] message by logging the reason and destroying the webview
     */
    private fun abort(reason: String) = close().also {
        Registry.log.info("IAF aborted, reason: $reason")
    }
}

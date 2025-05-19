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
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.webview.WebViewClient

/**
 * An instance of this class is injected into a [com.klaviyo.forms.webview.KlaviyoWebView] as a global property
 * on the window. It receives and interprets messages from klaviyo.js over the native bridge
 */
internal class KlaviyoBridgeMessageHandler() : BridgeMessageHandler {

    /**
     * This is the name that will be used to access the bridge from JS, i.e. window.KlaviyoNativeBridge
     */
    override val name = "KlaviyoNativeBridge"

    /**
     * The allowed origin for the webview content and bridge
     */
    override val allowedOrigin: Set<String> get() = setOf(Registry.config.baseUrl)

    /**
     * Handshake data indicating the message types/versions that the SDK supports receiving over the NativeBridge
     */
    override val handshake: List<HandshakeSpec> = BridgeMessage.handShakeData

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
                BridgeMessage.JsReady -> jsReady()
                BridgeMessage.HandShook -> handShook()
                is BridgeMessage.FormWillAppear -> show(bridgeMessage)
                is BridgeMessage.TrackAggregateEvent -> createAggregateEvent(bridgeMessage)
                is BridgeMessage.TrackProfileEvent -> createProfileEvent(bridgeMessage)
                is BridgeMessage.OpenDeepLink -> deepLink(bridgeMessage)
                is BridgeMessage.FormDisappeared -> close()
                is BridgeMessage.Abort -> abort(bridgeMessage.reason)
            }
        } catch (e: Exception) {
            Registry.log.error("Failed to relay webview message: $message", e)
        }
    }

    /**
     * Notify the client that the local JS scripts are loaded
     */
    private fun jsReady() = Registry.get<WebViewClient>().onLocalJsReady()

    /**
     * Notify the client that the handshake has completed
     */
    private fun handShook() = Registry.get<WebViewClient>().onJsHandshakeCompleted()

    /**
     * Notify the client that the webview should be shown
     */
    private fun show(bridgeMessage: BridgeMessage.FormWillAppear) = Registry.get<PresentationManager>().present().also {
        Registry.log.debug("Present form ${bridgeMessage.formId}")
    }

    /**
     * Handle a [BridgeMessage.TrackAggregateEvent] message by creating an API call
     */
    private fun createAggregateEvent(message: BridgeMessage.TrackAggregateEvent) =
        Registry.get<ApiClient>().enqueueAggregateEvent(message.payload)

    /**
     * Handle a [BridgeMessage.TrackProfileEvent] message by creating an API call
     */
    private fun createProfileEvent(message: BridgeMessage.TrackProfileEvent) =
        Klaviyo.createEvent(message.event)

    /**
     * Handle a [BridgeMessage.OpenDeepLink] message by broadcasting an intent to the host app
     * similar to how we handle deep links from a notification
     */
    private fun deepLink(messageType: BridgeMessage.OpenDeepLink) {
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
    private fun close() = Registry.get<PresentationManager>().dismiss()

    /**
     * Handle a [BridgeMessage.Abort] message by logging the reason and destroying the webview
     */
    private fun abort(reason: String) = close().also {
        Registry.log.info("IAF aborted, reason: $reason")
    }
}

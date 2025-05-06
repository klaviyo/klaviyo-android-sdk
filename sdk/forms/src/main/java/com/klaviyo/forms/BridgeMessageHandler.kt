package com.klaviyo.forms

import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.Registry

/**
 * Manages [KlaviyoWebView] to power in-app forms
 */
internal class BridgeMessageHandler(
    private val client: KlaviyoWebViewClient = Registry.get()
) : WebViewCompat.WebMessageListener {
    /**
     * This bridge object is injected into a [KlaviyoWebView] as a global property on the window.
     * This is the name that will be used to access the bridge from JS, i.e. window.KlaviyoNativeBridge
     */
    val name = "KlaviyoNativeBridge"

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
    @JavascriptInterface
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
     * Notify the client that the handshake has completed
     */
    private fun handShook() = client.onJsHandshakeCompleted()

    /**
     * Notify the client that the webview should be shown
     * TODO - move to presentation manager
     */
    private fun show() = client.show()

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
        Registry.config.applicationContext.startActivity(
            Intent().apply {
                data = messageType.route.toUri()
                action = Intent.ACTION_VIEW
                setPackage(Registry.config.applicationContext.packageName)
                setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    /**
     * Notify the client that the webview should be closed
     * TODO - move to presentation manager
     */
    private fun close() = client.close()

    /**
     * Handle a [BridgeMessage.Abort] message by logging the reason and destroying the webview
     */
    private fun abort(reason: String) = client.close().also {
        Registry.log.info("IAF aborted, reason: $reason")
    }
}

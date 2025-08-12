package com.klaviyo.forms.bridge

import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.Registry
import com.klaviyo.forms.bridge.NativeBridgeMessage.Abort
import com.klaviyo.forms.bridge.NativeBridgeMessage.FormDisappeared
import com.klaviyo.forms.bridge.NativeBridgeMessage.FormWillAppear
import com.klaviyo.forms.bridge.NativeBridgeMessage.HandShook
import com.klaviyo.forms.bridge.NativeBridgeMessage.JsReady
import com.klaviyo.forms.bridge.NativeBridgeMessage.OpenDeepLink
import com.klaviyo.forms.bridge.NativeBridgeMessage.TrackAggregateEvent
import com.klaviyo.forms.bridge.NativeBridgeMessage.TrackProfileEvent
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.unregisterFromInAppForms
import com.klaviyo.forms.webview.WebViewClient

/**
 * An instance of this class is injected into a [com.klaviyo.forms.webview.KlaviyoWebView] as a global property
 * on the window. It receives and interprets messages from klaviyo.js over the native bridge
 */
internal class KlaviyoNativeBridge() : NativeBridge {

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
    override val handshake: List<HandshakeSpec> = NativeBridgeMessage.handShakeData

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
            when (val bridgeMessage = NativeBridgeMessage.decodeWebviewMessage(message)) {
                JsReady -> jsReady()
                HandShook -> handShook()
                is FormWillAppear -> show(bridgeMessage)
                is TrackAggregateEvent -> createAggregateEvent(bridgeMessage)
                is TrackProfileEvent -> createProfileEvent(bridgeMessage)
                is OpenDeepLink -> deepLink(bridgeMessage)
                is FormDisappeared -> close()
                is Abort -> abort(bridgeMessage.reason)
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
    private fun show(bridgeMessage: FormWillAppear) = Registry.get<PresentationManager>()
        .present(bridgeMessage.formId)

    /**
     * Handle a [TrackAggregateEvent] message by creating an API call
     */
    private fun createAggregateEvent(message: TrackAggregateEvent) =
        Registry.get<ApiClient>().enqueueAggregateEvent(message.payload)

    /**
     * Handle a [TrackProfileEvent] message by creating an API call
     */
    private fun createProfileEvent(message: TrackProfileEvent) =
        Klaviyo.createEvent(message.event)

    /**
     * Handle a [OpenDeepLink] message by broadcasting an intent to the host app
     * similar to how we handle deep links from a notification
     *
     * There is a brief window between our overlay activity pausing and the next activity resuming.
     * We alleviate this race condition by postponing till next activity resumes if current activity is null.
     */
    private fun deepLink(message: OpenDeepLink) = DeepLinking.handleDeepLink(message.route.toUri())

    /**
     * Instruct presentation manager to dismiss the form overlay activity
     */
    private fun close() = Registry.get<PresentationManager>().dismiss()

    /**
     * Handle a [Abort] message by logging the reason and destroying the webview
     */
    private fun abort(reason: String) = Klaviyo.unregisterFromInAppForms().also {
        Registry.log.error("IAF aborted, reason: $reason")
    }
}

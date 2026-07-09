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
import com.klaviyo.core.Constants.ALLOWED_OPEN_URL_SCHEMES
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.startActivityIfResolved
import com.klaviyo.forms.FormLifecycleEvent
import com.klaviyo.forms.FormLifecycleHandler
import com.klaviyo.forms.bridge.NativeBridgeMessage.Abort
import com.klaviyo.forms.bridge.NativeBridgeMessage.FormDisappeared
import com.klaviyo.forms.bridge.NativeBridgeMessage.FormWillAppear
import com.klaviyo.forms.bridge.NativeBridgeMessage.HandShook
import com.klaviyo.forms.bridge.NativeBridgeMessage.JsReady
import com.klaviyo.forms.bridge.NativeBridgeMessage.OpenDeepLink
import com.klaviyo.forms.bridge.NativeBridgeMessage.OpenExternalUrl
import com.klaviyo.forms.bridge.NativeBridgeMessage.TrackAggregateEvent
import com.klaviyo.forms.bridge.NativeBridgeMessage.TrackProfileEvent
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.unregisterFromInAppForms
import com.klaviyo.forms.webview.WebViewClient

/**
 * An instance of this class is injected into a [com.klaviyo.forms.webview.KlaviyoWebView] as a global property
 * on the window. It receives and interprets messages from klaviyo.js over the native bridge
 */
internal class KlaviyoNativeBridge : NativeBridge {

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
                is OpenExternalUrl -> openExternalUrl(bridgeMessage)
                is FormDisappeared -> close(bridgeMessage)
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
    private fun show(bridgeMessage: FormWillAppear) {
        Registry.get<PresentationManager>().present(bridgeMessage.formId, bridgeMessage.layout)

        if (bridgeMessage.formId.isEmpty() || bridgeMessage.formName.isEmpty()) {
            Registry.log.warning(
                "FormWillAppear missing required fields, skipping lifecycle callback"
            )
            return
        }

        invokeFormLifecycleHandler(
            FormLifecycleEvent.FormShown(bridgeMessage.formId, bridgeMessage.formName)
        )
    }

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
    private fun deepLink(message: OpenDeepLink) {
        val deepLinkUri = message.route?.toUri()

        if (deepLinkUri == null) {
            Registry.log.warning("Form CTA with no Android route configured: ${message.formId}")
            return
        }

        DeepLinking.handleDeepLink(deepLinkUri)

        if (message.formId.isEmpty() || message.formName.isEmpty()) {
            Registry.log.warning(
                "OpenDeepLink missing required fields, skipping lifecycle callback"
            )
            return
        }

        invokeFormLifecycleHandler(
            FormLifecycleEvent.FormCtaClicked(
                formId = message.formId,
                formName = message.formName,
                buttonLabel = message.buttonLabel,
                deepLinkUrl = deepLinkUri
            )
        )
    }

    /**
     * Handle an [OpenExternalUrl] message by opening the URL in the default browser.
     *
     * Unlike [deepLink], the intent is not package-scoped (no `setPackage`), so the OS routes it
     * to the default browser, bypassing any registered deep link handler — mirroring
     * [com.klaviyo.forms.webview.KlaviyoWebViewClient.shouldOverrideUrlLoading]. The `NEW_TASK`
     * intent launches independently of the overlay activity, so no grace period is needed.
     * Fires [FormLifecycleEvent.FormCtaExternalUrlClicked] after dispatch.
     *
     * The URL's scheme is checked against [ALLOWED_OPEN_URL_SCHEMES] before dispatch — the same
     * allowlist gate applied to push's `open_url`/`web_url` fields (see
     * [com.klaviyo.pushFcm.KlaviyoRemoteMessage], PUSH-834) — to avoid routing dangerous or
     * unintended URIs (e.g. `intent:`, `javascript:`, `file:`) through the SDK. `smsto:` is
     * included for both platforms since Android has a handler for it (iOS omits it only because
     * iOS Messages has no `smsto:` handler). The intent itself is built by
     * [DeepLinking.makeExternalIntent], shared with the push `open_url` path.
     */
    private fun openExternalUrl(message: OpenExternalUrl) {
        val externalUri = message.url.toUri()

        if (externalUri.scheme?.lowercase() !in ALLOWED_OPEN_URL_SCHEMES) {
            Registry.log.warning(
                "openExternalUrl url '$externalUri' has a scheme not in the allowed list; ignoring."
            )
            return
        }

        DeepLinking.makeExternalIntent(externalUri).startActivityIfResolved(
            Registry.config.applicationContext
        )

        if (message.formId.isEmpty() || message.formName.isEmpty()) {
            Registry.log.warning(
                "OpenExternalUrl missing required fields, skipping lifecycle callback"
            )
            return
        }

        invokeFormLifecycleHandler(
            FormLifecycleEvent.FormCtaExternalUrlClicked(
                formId = message.formId,
                formName = message.formName,
                buttonLabel = message.buttonLabel,
                externalUrl = externalUri
            )
        )
    }

    /**
     * Instruct presentation manager to dismiss the form overlay activity.
     */
    private fun close(bridgeMessage: FormDisappeared) {
        Registry.get<PresentationManager>().dismiss()

        if (bridgeMessage.formId.isEmpty() || bridgeMessage.formName.isEmpty()) {
            Registry.log.warning(
                "FormDisappeared missing required fields, skipping lifecycle callback"
            )
            return
        }

        invokeFormLifecycleHandler(
            FormLifecycleEvent.FormDismissed(bridgeMessage.formId, bridgeMessage.formName)
        )
    }

    /**
     * Handle a [Abort] message by logging the reason and destroying the webview
     */
    private fun abort(reason: String) = Klaviyo.unregisterFromInAppForms().also {
        Registry.log.error("IAF aborted, reason: $reason")
    }

    /**
     * Invoke the registered form lifecycle callback on the main thread, if one is registered.
     * Dispatches to main thread for consistency across bridge paths:
     * Modern WebView versions, using [WEB_MESSAGE_LISTENER] are already on main thread,
     * but [JavascriptInterface] sends its messages on a background thread.
     */
    private fun invokeFormLifecycleHandler(event: FormLifecycleEvent) {
        Registry.getOrNull<FormLifecycleHandler>()?.let { callback ->
            Registry.threadHelper.runOnUiThread {
                try {
                    callback.onFormLifecycleEvent(event)
                } catch (e: Exception) {
                    Registry.log.error("Form lifecycle callback threw an exception", e)
                }
            }
        }
    }
}

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
                is OpenDeepLink -> openCtaUrl(bridgeMessage)
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
     * Handle an [OpenDeepLink] message by opening its URL. Both in-app deep links and external
     * web/system URLs arrive here; [OpenDeepLink.openExternally] — not the scheme — decides how to
     * open, preserving the marketer's chosen CTA action.
     *
     * When [OpenDeepLink.openExternally] is false, the URL is routed as an in-app deep link (like a
     * notification deep link). There is a brief window between our overlay activity pausing and the
     * next activity resuming; [DeepLinking.handleDeepLink] alleviates that race by postponing until
     * the next activity resumes if the current activity is null.
     *
     * When true, the URL is opened in the default browser via a non-package-scoped intent, bypassing
     * any registered deep link handler — mirroring
     * [com.klaviyo.forms.webview.KlaviyoWebViewClient.shouldOverrideUrlLoading]. The `NEW_TASK` intent
     * launches independently of the overlay activity, so no grace period is needed. The scheme is
     * checked against [ALLOWED_OPEN_URL_SCHEMES] first — the same allowlist gate applied to push's
     * `open_url`/`web_url` fields (see [com.klaviyo.pushFcm.KlaviyoRemoteMessage], PUSH-834) — to
     * avoid routing dangerous or unintended URIs (e.g. `intent:`, `javascript:`, `file:`). The intent
     * is built by [DeepLinking.makeExternalIntent], shared with the push `open_url` path.
     *
     * Fires [FormLifecycleEvent.FormCtaClicked] after dispatch, with the URL carried in
     * [FormLifecycleEvent.FormCtaClicked.deepLinkUrl].
     */
    private fun openCtaUrl(message: OpenDeepLink) {
        val uri = message.route?.toUri()

        if (uri == null) {
            Registry.log.warning("Form CTA with no Android route configured: ${message.formId}")
            return
        }

        if (message.openExternally) {
            if (uri.scheme?.lowercase() !in ALLOWED_OPEN_URL_SCHEMES) {
                Registry.log.warning(
                    "Form CTA external url '$uri' has a scheme not in the allowed list; ignoring."
                )
                return
            }

            DeepLinking.makeExternalIntent(uri).startActivityIfResolved(
                Registry.config.applicationContext
            )
        } else {
            DeepLinking.handleDeepLink(uri)
        }

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
                deepLinkUrl = uri
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

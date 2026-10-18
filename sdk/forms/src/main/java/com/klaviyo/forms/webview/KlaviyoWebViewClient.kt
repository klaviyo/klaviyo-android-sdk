package com.klaviyo.forms.webview

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient as AndroidWebViewClient
import androidx.core.net.toUri
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER
import androidx.webkit.WebViewFeature.isFeatureSupported
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenException
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.config.Clock
import com.klaviyo.core.safeLaunch
import com.klaviyo.core.utils.WeakReferenceDelegate
import com.klaviyo.core.utils.startActivityIfResolved
import com.klaviyo.forms.InAppFormsConfig
import com.klaviyo.forms.InAppFormsFailureHandler
import com.klaviyo.forms.bridge.DeviceInfoProvider
import com.klaviyo.forms.bridge.HandshakeSpec
import com.klaviyo.forms.bridge.JsBridge
import com.klaviyo.forms.bridge.JsBridgeObserverCollection
import com.klaviyo.forms.bridge.JwtObserver
import com.klaviyo.forms.bridge.NativeBridge
import com.klaviyo.forms.bridge.NativeBridgeMessage
import com.klaviyo.forms.bridge.compileJson
import com.klaviyo.forms.bridge.toBridgeJson
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.unregisterFromInAppForms
import java.io.BufferedReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Manages the [KlaviyoWebView] instance that powers In-App Forms behavior, triggering, rendering and display,
 * and handles all its [android.webkit.WebViewClient] delegate methods, and loading of klaviyo.js
 */
internal class KlaviyoWebViewClient() : AndroidWebViewClient(), WebViewClient, JavaScriptEvaluator {

    /**
     * For timeout on awaiting the native bridge [com.klaviyo.forms.bridge.NativeBridgeMessage.HandShook] event
     * as an indicator that klaviyo.js has loaded and the onsite-in-app module is present.
     */
    private var handshakeTimer: Clock.Cancellable? = null

    /**
     * Weak reference to the WebView to avoid memory leak
     */
    private var webView: KlaviyoWebView? by WeakReferenceDelegate()

    /**
     * Pending load of the forms page, awaiting the initial auth token. Cancelled by
     * [destroyWebView].
     */
    private var loadJob: Job? = null

    private var eventReplayWindow = InAppFormsConfig.DEFAULT_EVENT_REPLAY_WINDOW
    private var onFailure: InAppFormsFailureHandler? = null
    private var handshakeCompleted = false

    private var formsDataLoaded = false
    private var pendingForm: PendingForm? = null
    private var companyId: String? = null

    private data class PendingForm(val formId: String, val arrivedAt: Long, val generation: Long)

    private val scope = CoroutineScope(SupervisorJob() + Registry.dispatcher)

    override fun openForm(formId: String) {
        if (formId.isBlank()) {
            Registry.log.warning("Cannot open a form with a blank ID.")
            return
        }
        val expectedView = webView ?: run {
            Registry.log.warning("Cannot open a form without an active Forms runtime.")
            return
        }
        val request = PendingForm(
            formId,
            Registry.clock.monotonicTimeMillis(),
            Registry.get<State>().bufferedEventGeneration
        )
        Registry.threadHelper.runOnUiThread {
            if (webView !== expectedView || companyId != Registry.config.apiKey ||
                request.generation != Registry.get<State>().bufferedEventGeneration
            ) {
                Registry.log.debug("Discarding manual form request from a retired context.")
            } else if (formsDataLoaded) {
                Registry.get<JsBridge>().openForm(request.formId)
            } else if (eventReplayWindow.isPositive()) {
                pendingForm = request
            } else {
                Registry.log.debug("Skipping manual form request while Forms data loads.")
            }
        }
    }

    override fun onFormsDataLoaded() {
        val expectedView = webView ?: return
        Registry.threadHelper.runOnUiThread {
            if (webView !== expectedView || companyId != Registry.config.apiKey) return@runOnUiThread
            formsDataLoaded = true
            val pending = pendingForm
            pendingForm = null
            pending?.let {
                if (it.generation == Registry.get<State>().bufferedEventGeneration &&
                    Registry.clock.monotonicTimeMillis() - it.arrivedAt < eventReplayWindow.inWholeMilliseconds
                ) {
                    Registry.get<JsBridge>().openForm(it.formId)
                } else {
                    Registry.log.debug("Discarding expired manual form request or retired context.")
                }
            }
        }
    }

    /**
     * Initialize a webview instance, with protection against duplication
     * and initialize klaviyo.js for In-App Forms with handshake data injected in the document head.
     *
     * The page is not loaded until the initial auth token arrives or
     * [AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS] expires, so no form can evaluate or render
     * before then. A token that arrives in time is seeded into the document head. After a timeout
     * the page loads without one and [JwtObserver] delivers the token over the JS bridge when the
     * still-running fetch completes.
     */
    override fun initializeWebView() = Registry.threadHelper.runOnUiThread {
        if (webView != null) {
            Registry.log.debug("Klaviyo webview is already initialized")
            return@runOnUiThread
        }

        val config = Registry.getOrNull<InAppFormsConfig>() ?: InAppFormsConfig()
        onFailure = config.getOnFailure()
        eventReplayWindow = config.getEventReplayWindow()
        handshakeCompleted = false
        formsDataLoaded = false
        pendingForm = null
        companyId = Registry.config.apiKey
        val webView = KlaviyoWebView().also { this.webView = it }
        val loadTimeout = config.getLoadTimeout()?.inWholeMilliseconds
            ?: Registry.config.networkTimeout.toLong()
        loadJob?.cancel()
        loadJob = scope.safeLaunch {
            val token = awaitInitialToken()
            Registry.threadHelper.runOnUiThread {
                if (this@KlaviyoWebViewClient.webView === webView) {
                    try {
                        loadTemplate(webView, token, loadTimeout)
                    } catch (e: Exception) {
                        failLoad(webView, "Unable to load In-App Forms template", e)
                    }
                }
            }
        }
    }

    /**
     * Wait up to [AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS] for the auth token. Returns null
     * without waiting when auth is not enabled or the profile is not identified, and null on
     * timeout or failure. A timeout does not cancel the manager's underlying fetch.
     */
    private suspend fun awaitInitialToken(): String? = try {
        Registry.get<AuthTokenManager>()
            .currentToken(AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS)
            .rawToken
    } catch (e: CancellationException) {
        throw e
    } catch (_: AuthTokenException.NoProviderRegistered) {
        null
    } catch (_: AuthTokenException.NotIdentified) {
        null
    } catch (e: Exception) {
        Registry.log.verbose("Loading In-App Forms without a JWT: ${e.javaClass.simpleName}")
        null
    }

    /**
     * Render the template and load it into [webView]. [token] is seeded only if it is still the
     * manager's current token, so a profile replaced while the token was awaited cannot receive
     * the previous profile's JWT. Must be called on the UI thread.
     */
    private fun loadTemplate(webView: KlaviyoWebView, token: String?, loadTimeout: Long) {
        val loadedCompanyId = Registry.config.apiKey
        val nativeBridge = Registry.get<NativeBridge>()
        val jsBridge = Registry.get<JsBridge>()
        val handshake: List<HandshakeSpec> = nativeBridge.handshake + jsBridge.handshake
        val jwt = token?.takeIf { Registry.get<AuthTokenManager>().isCurrentToken(it) }.orEmpty()

        val klaviyoJsUrl = Registry.config.baseCdnUrl.toUri()
            .buildUpon()
            .path("onsite/js/klaviyo.js")
            .appendQueryParameter("company_id", loadedCompanyId)
            .appendQueryParameter("env", "in-app")
            .appendAssetSource()
            .build()

        // DeviceInfoProvider.current() reads UI-thread-only APIs (Display.rotation,
        // decorView.rootWindowInsets), so the template is rendered on the main thread.
        val template = Registry.config.applicationContext.assets
            .open("InAppFormsTemplate.html")
            .bufferedReader()
            .use(BufferedReader::readText)
        val partialHtml = template.replaceTemplateValues(
            mapOf(
                "SDK_NAME" to Registry.config.sdkName,
                "SDK_VERSION" to Registry.config.sdkVersion,
                "BRIDGE_NAME" to nativeBridge.name,
                "BRIDGE_HANDSHAKE" to handshake.compileJson(),
                "KLAVIYO_JS_URL" to klaviyoJsUrl.toString(),
                "FORMS_ENVIRONMENT" to Registry.config.formEnvironment.templateName,
                "KLAVIYO_PROFILE" to Registry.get<State>().getAsProfile().toBridgeJson(),
                "KLAVIYO_JWT" to jwt,
                "DEVICE_INFO" to DeviceInfoProvider.current().toJson()
            )
        )

        handshakeTimer?.cancel()
        handshakeTimer = Registry.clock.schedule(
            loadTimeout
        ) { failLoad(webView, "IAF WebView Aborted: Timeout waiting for Klaviyo.js") }
        companyId = loadedCompanyId
        webView.loadTemplate(partialHtml, this, nativeBridge.forWebView(webView))
    }

    private fun NativeBridge.forWebView(expectedView: KlaviyoWebView): NativeBridge {
        val delegate = this
        return object : NativeBridge by delegate {
            @JavascriptInterface
            override fun postMessage(message: String) = Registry.threadHelper.runOnUiThread {
                if (webView === expectedView) delegate.postMessage(message)
            }

            override fun onPostMessage(
                view: WebView,
                message: WebMessageCompat,
                sourceOrigin: Uri,
                isMainFrame: Boolean,
                replyProxy: JavaScriptReplyProxy
            ) = Registry.threadHelper.runOnUiThread {
                if (webView === expectedView && view === expectedView) {
                    delegate.onPostMessage(view, message, sourceOrigin, isMainFrame, replyProxy)
                }
            }
        }
    }

    override fun onLocalJsReady() = Registry.threadHelper.runOnUiThread {
        if (webView != null) {
            Registry.get<JsBridgeObserverCollection>().startObservers(NativeBridgeMessage.JsReady)
        }
    }

    /**
     * When the webview has loaded klaviyo.js, we can cancel the timeout
     */
    override fun onJsHandshakeCompleted() = Registry.threadHelper.runOnUiThread {
        if (webView != null && !handshakeCompleted) {
            handshakeCompleted = true
            onFailure = null
            handshakeTimer?.cancel()
            handshakeTimer = null
            Registry.get<JsBridgeObserverCollection>().startObservers(
                NativeBridgeMessage.HandShook,
                eventReplayWindow
            )
        }
    }

    override fun onJsAbort(reason: String) {
        val expectedView = webView ?: return
        Registry.threadHelper.runOnUiThread {
            if (webView !== expectedView) return@runOnUiThread
            if (handshakeCompleted) {
                Klaviyo.unregisterFromInAppForms()
            } else {
                failLoad(expectedView, "IAF WebView Aborted: $reason")
            }
        }
    }

    /**
     * Get the underlying WebView instance, or null if not initialized
     */
    override fun getWebView(): View? = webView

    /**
     * Retire the failed attempt before notifying its handler on the main thread.
     */
    private fun failLoad(expectedView: WebView?, message: String, error: Exception? = null) =
        Registry.threadHelper.runOnUiThread {
            if (expectedView == null || webView !== expectedView || handshakeCompleted) return@runOnUiThread
            val handler = onFailure
            if (error == null) Registry.log.warning(message) else Registry.log.error(message, error)
            destroyWebViewOnMain()
            try {
                handler?.onFailure()
            } catch (e: Exception) {
                Registry.log.error("In-App Forms failure callback threw an exception", e)
            }
        }

    /**
     * Attach the webview to the overlay activity
     */
    override fun attachWebView(activity: Activity) = apply {
        webView?.let { webView ->
            Registry.threadHelper.runOnUiThread {
                activity.setContentView(webView)
                webView.visibility = View.VISIBLE
            }
        } ?: run {
            Registry.log.warning("Unable to attach IAF - null WebView reference")
        }
    }

    /**
     * Detach the webview from the overlay activity, keeping it in memory
     */
    override fun detachWebView() = apply {
        webView?.let { webView ->
            Registry.threadHelper.runOnUiThread {
                webView.visibility = View.GONE
                webView.parent?.let { it as ViewGroup }?.removeView(webView)
            }
        } ?: run {
            Registry.log.warning("Unable to detach IAF - null WebView reference")
        }
    }

    /**
     * Destroy the webview and release the reference
     */
    override fun destroyWebView() = apply {
        val expectedView = webView
        Registry.threadHelper.runOnUiThread {
            if (webView === expectedView) destroyWebViewOnMain()
        }
    }

    private fun destroyWebViewOnMain() {
        val webView = webView
        this.webView = null
        onFailure = null
        handshakeCompleted = false
        formsDataLoaded = false
        pendingForm = null
        companyId = null
        loadJob?.cancel()
        loadJob = null
        handshakeTimer?.cancel()
        handshakeTimer = null
        Registry.get<JsBridgeObserverCollection>().stopObservers()
        webView?.let {
            Registry.log.verbose("Clear IAF WebView reference")
            val nativeBridge = Registry.get<NativeBridge>()

            if (isFeatureSupported(WEB_MESSAGE_LISTENER)) {
                // Some vendors' WebViews retain the message listener after destruction.
                WebViewCompat.removeWebMessageListener(webView, nativeBridge.name)
            } else {
                webView.removeJavascriptInterface(nativeBridge.name)
            }

            webView.parent?.let { it as ViewGroup }?.removeView(webView)
            webView.destroy()
        } ?: run {
            Registry.log.warning("Unable to destroy IAF - null WebView reference")
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
        if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
            failLoad(view, "IAF WebView Aborted: Main frame HTTP failure")
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        if (request?.isForMainFrame == true) {
            failLoad(view, "IAF WebView Aborted: Main frame resource failure")
        }
    }

    @Deprecated("Used by WebView versions before API 23")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        failLoad(view, "IAF WebView Aborted: Main frame resource failure ($errorCode)")
    }

    /**
     * When an assetSource is specified, log whether we actually got it
     */
    override fun onPageFinished(view: WebView?, url: String?) = Registry.config.assetSource?.let { expected ->
        view?.evaluateJavascript("window.klaviyoModulesObject?.assetSource") { actual ->
            Registry.log.debug("Actual Asset Source: $actual. Expected $expected")
        }
    } ?: Unit

    /**
     * If the webview renderer crashes or gets cleaned up to reclaim memory,
     * we have to clean up and return true here else the host app will crash
     */
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        Registry.threadHelper.runOnUiThread {
            if (view == null || webView !== view) return@runOnUiThread
            Registry.get<PresentationManager>().dismiss()
            Registry.log.error("WebView crashed or deallocated")
            if (handshakeCompleted) {
                destroyWebViewOnMain()
            } else {
                failLoad(view, "IAF WebView Aborted: Renderer failure")
            }
        }
        return true
    }

    /**
     * Intercept page navigation and redirect to an external browser application
     */
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (request?.isForMainFrame == true) {
            Registry.log.info("Redirect URL to external browser: ${request.url}")
            val intent = Intent().apply {
                data = request.url
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            intent.startActivityIfResolved(Registry.config.applicationContext)
            return true
        }
        return false
    }

    /**
     * Re-publish the current [com.klaviyo.forms.bridge.DeviceInfo] snapshot to the webview's
     * `data-klaviyo-device` head attribute. This keeps onsite-in-app in sync with orientation
     * changes and safe-area inset updates without reloading the template.
     */
    override fun pushDeviceInfo() {
        webView?.let { webView ->
            // DeviceInfoProvider.current() reads UI-thread-only APIs (Display.rotation,
            // decorView.rootWindowInsets). Building the snapshot off-thread would yield silently
            // degraded payloads because DeviceInfoProvider swallows CalledFromWrongThreadException.
            // Dispatch the entire body — snapshot, serialize, evaluate — onto the UI thread.
            Registry.threadHelper.runOnUiThread {
                // JSON is a subset of JS: embedding the raw JSON as a JS object expression and
                // letting the engine stringify it avoids any JS string-literal escaping concerns.
                val json = DeviceInfoProvider.current().toJson()
                val script = "document.head.setAttribute('data-klaviyo-device', JSON.stringify($json))"
                webView.evaluateJavascript(script, null)
            }
        }
    }

    /**
     * Evaluate JavaScript in the webview, if possible
     */
    override fun evaluateJavascript(
        javascript: String,
        callback: (Boolean) -> Unit
    ) = webView?.let { webView ->
        Registry.threadHelper.runOnUiThread {
            webView.evaluateJavascript(javascript) { result ->
                callback(result == "true")
            }
        }
    } ?: run {
        Registry.log.warning("Unable to evaluate Javascript - null WebView reference")
        callback(false)
    }

    /**
     * Helper to append the asset source to klaviyo.js URL
     */
    private fun Uri.Builder.appendAssetSource() = Registry.config.assetSource?.let { assetSource ->
        Registry.log.debug("Appending assetSource=$assetSource to klaviyo.js")
        appendQueryParameter("assetSource", assetSource)
    } ?: this
}

private fun String.escapeHtmlAttribute(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

/**
 * Replace each key of [values] with its HTML-attribute-escaped value in a single pass, so
 * substituted values are never themselves scanned for placeholders. Every placeholder must sit
 * inside an HTML attribute value.
 */
private fun String.replaceTemplateValues(values: Map<String, String>): String =
    Regex(values.keys.joinToString("|", transform = Regex::escape))
        .replace(this) { match -> values.getValue(match.value).escapeHtmlAttribute() }

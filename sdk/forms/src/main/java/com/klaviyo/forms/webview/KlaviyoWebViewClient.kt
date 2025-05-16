package com.klaviyo.forms.webview

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient as AndroidWebViewClient
import androidx.core.net.toUri
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.utils.WeakReferenceDelegate
import com.klaviyo.forms.InAppFormsConfig
import com.klaviyo.forms.bridge.BridgeMessage.Companion.handShakeData
import com.klaviyo.forms.bridge.BridgeMessageHandler
import com.klaviyo.forms.presentation.KlaviyoPresentationManager
import java.io.BufferedReader

/**
 * Manages the [KlaviyoWebView] instance that powers in-app forms behavior, triggering, rendering and display,
 * and handles all its [android.webkit.WebViewClient] delegate methods, and loading of klaviyo.js
 */
internal class KlaviyoWebViewClient(
    val config: InAppFormsConfig = InAppFormsConfig()
) : AndroidWebViewClient(), WebViewClient, JavaScriptEvaluator {

    /**
     * For timeout on awaiting the native bridge [com.klaviyo.forms.bridge.BridgeMessage.HandShook] event
     * as an indicator that klaviyo.js has loaded and the onsite-in-app module is present.
     */
    private var handshakeTimer: Clock.Cancellable? = null

    /**
     * Weak reference to the WebView to avoid memory leak
     */
    private var webView: KlaviyoWebView? by WeakReferenceDelegate()

    init {
        /**
         * Self-register self as JavaScriptEvaluator
         */
        Registry.register<JavaScriptEvaluator>(this)
    }

    /**
     * Initialize a webview instance, with protection against duplication
     * and initialize klaviyo.js for in-app forms with handshake data injected in the document head
     */
    override fun initializeWebView(): Unit = webView?.let {
        Registry.log.debug("Klaviyo webview is already initialized")
    } ?: KlaviyoWebView().let { webView ->
        val nativeBridge: BridgeMessageHandler = Registry.get()
        this.webView = webView

        val klaviyoJsUrl = Registry.config.baseCdnUrl.toUri()
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
            .replace("BRIDGE_NAME", nativeBridge.name)
            .replace("BRIDGE_HANDSHAKE", handShakeData)
            .replace("KLAVIYO_JS_URL", klaviyoJsUrl.toString())
            .replace("FORMS_ENVIRONMENT", Registry.config.formEnvironment.templateName)
            .let { html ->
                webView.loadTemplate(html, this, nativeBridge)
                handshakeTimer?.cancel()
                handshakeTimer = Registry.clock.schedule(
                    Registry.config.networkTimeout.toLong(),
                    ::onJsHandshakeTimeout
                )
            }
    }

    /**
     * When the webview has loaded klaviyo.js, we can cancel the timeout
     */
    override fun onJsHandshakeCompleted() {
        handshakeTimer?.cancel()
        handshakeTimer = null
    }

    /**
     * If the webview is not loaded in time, we cancel the handshake timer and destroy the webview
     * TODO - retrying preload with exponential backoff and network monitoring
     */
    private fun onJsHandshakeTimeout() {
        handshakeTimer?.cancel()
        Registry.log.debug("IAF WebView Aborted: Timeout waiting for Klaviyo.js")
        Registry.lifecycleMonitor.currentActivity?.let {
            detachWebView(it)
        } ?: destroyWebView()
    }

    /**
     * Attach the webview to the overlay activity
     */
    override fun attachWebView(activity: Activity) = apply {
        webView?.let { webView ->
            activity.runOnUiThread {
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
    override fun detachWebView(activity: Activity) = apply {
        webView?.let { webView ->
            webView.visibility = View.GONE
            webView.parent?.let { it as ViewGroup }?.removeView(webView)
            destroyWebView()
        } ?: run {
            Registry.log.warning("Unable to detach IAF - null WebView reference")
        }
    }

    /**
     * Destroy the webview and release the reference
     */
    override fun destroyWebView() = apply {
        handshakeTimer?.cancel()
        webView?.let { webView ->
            Registry.log.verbose("Clear IAF WebView reference")
            webView.destroy()
            this.webView = null
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
    ) = Registry.log.warning("HTTP Error: ${errorResponse?.statusCode} - ${request?.url}")

    /**
     * When an assetSource is specified, log whether we actually got it
     */
    override fun onPageFinished(view: WebView?, url: String?) = Registry.config.assetSource?.let { expected ->
        webView?.evaluateJavascript("window.klaviyoModulesObject?.assetSource") { actual ->
            Registry.log.debug("Actual Asset Source: $actual. Expected $expected")
        }
    } ?: Unit

    /**
     * If the webview renderer crashes or gets cleaned up to reclaim memory,
     * we have to clean up and return true here else the host app will crash
     */
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean =
        Registry.get<KlaviyoPresentationManager>().dismiss().let {
            Registry.log.error("WebView crashed or deallocated")
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
            Registry.config.applicationContext.startActivity(intent, null)
            return true
        }
        return false
    }

    /**
     * Evaluate JavaScript in the webview, if possible
     */
    override fun evaluateJavascript(
        javascript: String,
        callback: (Boolean) -> Unit
    ) = webView?.let { webView ->
        Registry.lifecycleMonitor.currentActivity?.runOnUiThread {
            webView.evaluateJavascript(javascript) { result ->
                callback(result === "true")
            }
        } ?: run {
            Registry.log.warning("Unable to evaluate Javascript - null activity reference")
            callback(false)
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

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
import androidx.core.net.toUri
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.utils.WeakReferenceDelegate
import com.klaviyo.forms.BridgeMessage.Companion.handShakeData
import java.io.BufferedReader

/**
 * Manages [KlaviyoWebView] to power in-app forms
 */
internal class KlaviyoWebViewClient : WebViewClient() {
    val nativeBridge: BridgeMessageHandler = BridgeMessageHandler(this)

    private val activity: Activity? get() = Registry.lifecycleMonitor.currentActivity

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
         *
         * TODO move to presentation manager
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
     * Handle a [BridgeMessage.HandShook] message by clearing the timer
     */
    fun onJsHandshakeCompleted() = handshakeTimer?.cancel()

    /**
     * Show the webview in the current activity
     * TODO move to presentation manager
     */
    fun show() = webView?.let { webView ->
        activity?.window?.decorView?.post { decorView ->
            decorView.findViewById<ViewGroup>(android.R.id.content).addView(webView)
            webView.visibility = View.VISIBLE
        } ?: run {
            Registry.log.warning("Unable to show IAF - null activity reference")
        }
    } ?: run {
        Registry.log.warning("Unable to show IAF - null WebView reference")
    }

    /**
     * Remove the webview from the current activity and destroy it
     * TODO move to presentation manager
     */
    fun close() = webView?.let { webView ->
        handshakeTimer?.cancel()
        activity?.window?.decorView?.post {
            Registry.log.verbose("Clear IAF WebView reference")
            this.webView = null
            webView.visibility = View.GONE
            webView.parent?.let { it as ViewGroup }?.removeView(webView)
            webView.destroy()
        } ?: run {
            Registry.log.warning("Unable to close IAF - null activity reference")
        }
    } ?: run {
        Registry.log.warning("Unable to close IAF - null WebView reference")
    }

    /**
     * View.post but with self as an argument
     */
    private fun View.post(fn: (View) -> Unit) = apply { post { fn(this) } }
}

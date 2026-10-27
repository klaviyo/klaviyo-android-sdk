package com.klaviyo.forms

import androidx.annotation.UiThread
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply
import com.klaviyo.forms.bridge.JsBridge
import com.klaviyo.forms.bridge.JsBridgeObserverCollection
import com.klaviyo.forms.bridge.KlaviyoJsBridge
import com.klaviyo.forms.bridge.KlaviyoNativeBridge
import com.klaviyo.forms.bridge.KlaviyoObserverCollection
import com.klaviyo.forms.bridge.NativeBridge
import com.klaviyo.forms.presentation.KlaviyoPresentationManager
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.preview.FormsPreviewTokenStore
import com.klaviyo.forms.webview.JavaScriptEvaluator
import com.klaviyo.forms.webview.KlaviyoWebViewClient
import com.klaviyo.forms.webview.WebViewClient

/**
 * Resets the In-App Forms listeners with the current configuration.
 */
@UiThread
internal fun Klaviyo.reInitializeInAppForms(): Klaviyo {
    if (!inAppIsRegistered()) {
        Registry.log.warning(
            "Cannot reInitializeInAppForms, registerForInAppForms must be called first."
        )
        return this
    }
    safeApply { tearDownInAppForms() }
    return safeApply { Registry.get<FormsProvider>().register(Registry.get<InAppFormsConfig>()) }
}

internal class KlaviyoFormsProvider : FormsProvider {
    /**
     * Whether the app has registered for in-app forms and not unregistered since. Services stay in
     * the [Registry] after [unregister], so [inAppIsRegistered] alone can't tell.
     */
    @Volatile
    private var isRegistered = false

    override fun register(config: InAppFormsConfig) {
        isRegistered = true
        Registry.apply {
            register<InAppFormsConfig>(config)
            registerOnce<FormsPreviewTokenStore> { FormsPreviewTokenStore() }
            registerOnce<PresentationManager> { KlaviyoPresentationManager() }
            registerOnce<NativeBridge> { KlaviyoNativeBridge() }
            registerOnce<WebViewClient> {
                KlaviyoWebViewClient().also { register<JavaScriptEvaluator>(it) }
            }
            registerOnce<JsBridge> { KlaviyoJsBridge() }
            registerOnce<JsBridgeObserverCollection> { KlaviyoObserverCollection() }
        }
        Registry.get<WebViewClient>().initializeWebView()
    }

    /**
     * onsite-in-app-forms only reads the preview token when klaviyo.js loads, so starting a
     * preview always starts a new session rather than updating a live one.
     */
    override fun startPreview(token: String) {
        val validated = FormsPreviewTokenStore.validate(token) ?: return
        previewTokenStore.setToken(validated)
        restartSession()
    }

    override fun endPreview() {
        previewTokenStore.clear()
        restartSession()
    }

    private val previewTokenStore: FormsPreviewTokenStore
        get() {
            Registry.registerOnce<FormsPreviewTokenStore> { FormsPreviewTokenStore() }
            return Registry.get<FormsPreviewTokenStore>()
        }

    /**
     * Tear down and start a new session if registered. Before register, the stored token is
     * read by the first session instead.
     */
    private fun restartSession() {
        if (isRegistered) {
            Registry.threadHelper.runOnUiThread { Klaviyo.reInitializeInAppForms() }
        } else {
            Registry.log.info("Forms preview will start when registerForInAppForms is called")
        }
    }

    /**
     * Also ends any forms preview, so registering again loads published forms.
     */
    override fun unregister() {
        isRegistered = false
        Registry.getOrNull<FormsPreviewTokenStore>()?.clear()
        if (inAppIsRegistered()) {
            tearDownInAppForms()
        } else {
            Registry.log.warning("Cannot unregister In-App Forms, must be registered first.")
        }
    }
}

/**
 * Dismiss any visible form and destroy the webview, keeping the preview token for the next session.
 */
private fun tearDownInAppForms() {
    Registry.get<PresentationManager>().dismiss()
    Registry.get<WebViewClient>().destroyWebView()
}

/**
 * Check if IAF services are registered in the Klaviyo registry.
 */
private fun inAppIsRegistered(): Boolean = listOf(
    Registry.getOrNull<InAppFormsConfig>(),
    Registry.getOrNull<PresentationManager>(),
    Registry.getOrNull<WebViewClient>(),
    Registry.getOrNull<JsBridge>(),
    Registry.getOrNull<NativeBridge>()
).all { it != null }

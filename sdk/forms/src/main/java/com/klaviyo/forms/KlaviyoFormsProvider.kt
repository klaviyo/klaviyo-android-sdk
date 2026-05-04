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
    safeApply { Registry.get<FormsProvider>().unregister() }
    return safeApply { Registry.get<FormsProvider>().register(Registry.get<InAppFormsConfig>()) }
}

internal class KlaviyoFormsProvider : FormsProvider {

    /**
     * JWT queued before [registerForInAppForms] was called; forwarded to [WebViewClient] on register.
     */
    private var pendingJwt: String? = null

    override fun register(config: InAppFormsConfig) {
        Registry.apply {
            register<InAppFormsConfig>(config)
            registerOnce<PresentationManager> { KlaviyoPresentationManager() }
            registerOnce<NativeBridge> { KlaviyoNativeBridge() }
            registerOnce<WebViewClient> {
                KlaviyoWebViewClient().also { register<JavaScriptEvaluator>(it) }
            }
            registerOnce<JsBridge> { KlaviyoJsBridge() }
            registerOnce<JsBridgeObserverCollection> { KlaviyoObserverCollection() }
        }
        Registry.get<WebViewClient>().initializeWebView()
        pendingJwt?.let { token ->
            pendingJwt = null
            Registry.get<WebViewClient>().setJWT(token)
        }
    }

    override fun unregister() {
        if (inAppIsRegistered()) {
            Registry.get<PresentationManager>().dismiss()
            Registry.get<WebViewClient>().destroyWebView()
        } else {
            Registry.log.warning("Cannot unregister In-App Forms, must be registered first.")
        }
    }

    override fun setJWT(token: String) {
        Registry.getOrNull<WebViewClient>()?.setJWT(token) ?: run {
            Registry.log.verbose("IAF not yet registered, queuing JWT for delivery on register")
            pendingJwt = token
        }
    }
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

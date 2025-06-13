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
 * Load in-app forms data and display a form to the user if applicable based on the forms
 * configured in your Klaviyo account. Note [Klaviyo.initialize] must be called first
 */
@UiThread
fun Klaviyo.registerForInAppForms(
    config: InAppFormsConfig = InAppFormsConfig()
): Klaviyo = safeApply {
    // Register IAF services
    Registry.apply {
        register<InAppFormsConfig>(config)
        registerOnce<PresentationManager> { KlaviyoPresentationManager() }
        registerOnce<NativeBridge> { KlaviyoNativeBridge() }
        registerOnce<WebViewClient> {
            KlaviyoWebViewClient().also {
                register<JavaScriptEvaluator>(it)
            }
        }
        registerOnce<JsBridge> { KlaviyoJsBridge() }
        registerOnce<JsBridgeObserverCollection> { KlaviyoObserverCollection() }
    }

    // And initialize the webview client
    Registry.get<WebViewClient>().initializeWebView()
}

/**
 * Halts the in-app forms services and observers,
 * hiding any currently displayed forms and preventing any further forms from being presented.
 */
@UiThread
fun Klaviyo.unregisterFromInAppForms() = safeApply {
    Registry.apply {
        if (inAppIsRegistered()) {
            get<PresentationManager>().dismiss()
            get<WebViewClient>().destroyWebView()
        } else {
            log.warning("Cannot unregister in-app forms, must be registered first.")
        }
    }
}

/**
 * Resets the in-app forms listeners with the current configuration.
 */
@UiThread
internal fun Klaviyo.reInitializeInAppForms() = safeApply {
    Registry.apply {
        if (inAppIsRegistered()) {
            unregisterFromInAppForms()
            registerForInAppForms(get<InAppFormsConfig>())
        } else {
            log.warning(
                "Cannot reInitializeInAppForms, registerForInAppForms must be called first."
            )
        }
    }
}

/**
 * Check if IAF services are registered in the Klaviyo registry.
 */
private fun Registry.inAppIsRegistered(): Boolean = listOf(
    getOrNull<InAppFormsConfig>(),
    getOrNull<PresentationManager>(),
    getOrNull<WebViewClient>(),
    getOrNull<JsBridge>(),
    getOrNull<NativeBridge>()
).all { it != null }

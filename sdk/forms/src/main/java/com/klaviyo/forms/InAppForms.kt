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
 * Start an In-App Forms session.
 *
 * This will load forms data and establish ongoing listeners to present a form to the user
 * whenever a form is triggered by an event or condition according to the targeting and behavior
 * settings configured for forms in your Klaviyo account.
 *
 * Note: a public API key is required, so [Klaviyo.initialize] must be called first.
 * If the API key changes, the session will be re-initialized automatically with the new key.
 *
 * @param config see [InAppFormsConfig] for configuration options.
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
 * Halt the In-App Forms services and observers,
 * hiding any currently displayed forms and preventing any further forms from being presented.
 */
@UiThread
fun Klaviyo.unregisterFromInAppForms() = safeApply {
    Registry.apply {
        if (inAppIsRegistered()) {
            get<PresentationManager>().dismiss()
            get<WebViewClient>().destroyWebView()
        } else {
            log.warning("Cannot unregister In-App Forms, must be registered first.")
        }
    }
}

/**
 * Resets the In-App Forms listeners with the current configuration.
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

/**
 * Java-friendly static methods for In-App Forms.
 * Kotlin users should use the extension functions on [Klaviyo] instead.
 */
object KlaviyoForms {
    /**
     * Start an In-App Forms session.
     * Java-friendly static method.
     *
     * @param config see [InAppFormsConfig] for configuration options.
     * @see Klaviyo.registerForInAppForms
     */
    @JvmStatic
    @JvmOverloads
    @UiThread
    fun registerForInAppForms(config: InAppFormsConfig = InAppFormsConfig()) {
        Klaviyo.registerForInAppForms(config)
    }

    /**
     * Halt the In-App Forms services and observers.
     * Java-friendly static method.
     *
     * @see Klaviyo.unregisterFromInAppForms
     */
    @JvmStatic
    @UiThread
    fun unregisterFromInAppForms() {
        Klaviyo.unregisterFromInAppForms()
    }
}

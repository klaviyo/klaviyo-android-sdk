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
    }

    override fun unregister() {
        if (inAppIsRegistered()) {
            Registry.get<PresentationManager>().dismiss()
            Registry.get<WebViewClient>().destroyWebView()
        } else {
            Registry.log.warning("Cannot unregister In-App Forms, must be registered first.")
        }
    }
}

/**
 * Register a handler to receive [FormLifecycleEvent] events.
 *
 * The handler is invoked whenever a form is shown, dismissed,
 * or a CTA button is clicked. Only one handler can be registered at a time;
 * calling this again replaces the previous registration.
 *
 * **Threading:** The handler is always invoked on the main thread.
 *
 * @param handler The [FormLifecycleHandler] to invoke on lifecycle events.
 */
fun Klaviyo.registerFormLifecycleHandler(handler: FormLifecycleHandler): Klaviyo =
    safeApply { Registry.register<FormLifecycleHandler>(handler) }

/**
 * Remove the previously registered form lifecycle handler.
 * After calling this, no further lifecycle events will be delivered.
 */
fun Klaviyo.unregisterFormLifecycleHandler(): Klaviyo =
    safeApply { Registry.unregister<FormLifecycleHandler>() }

/**
 * Java-friendly static methods for form lifecycle handlers.
 * Kotlin users should use the extension functions on [Klaviyo] instead.
 *
 * Note: These wrappers live in the `forms` module (rather than in [KlaviyoForms] in `forms-core`)
 * because [FormLifecycleHandler] is defined in the `forms` module and `forms-core` cannot depend
 * on `forms`.
 */
object KlaviyoFormLifecycleHandlers {
    /**
     * Register a handler to receive form lifecycle events.
     * Java-friendly static method.
     *
     * @param handler The [FormLifecycleHandler] to invoke on lifecycle events.
     * @see Klaviyo.registerFormLifecycleHandler
     */
    @JvmStatic
    fun registerFormLifecycleHandler(handler: FormLifecycleHandler) {
        Klaviyo.registerFormLifecycleHandler(handler)
    }

    /**
     * Remove the previously registered form lifecycle handler.
     * Java-friendly static method.
     *
     * @see Klaviyo.unregisterFormLifecycleHandler
     */
    @JvmStatic
    fun unregisterFormLifecycleHandler() {
        Klaviyo.unregisterFormLifecycleHandler()
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

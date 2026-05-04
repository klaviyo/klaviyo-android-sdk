package com.klaviyo.forms

import androidx.annotation.UiThread
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply

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
 * @throws MissingKlaviyoModule if the `com.klaviyo:forms` module is not on the classpath.
 */
@UiThread
fun Klaviyo.registerForInAppForms(
    config: InAppFormsConfig = InAppFormsConfig()
): Klaviyo = Registry.getOrNull<FormsProvider>()?.let { provider ->
    safeApply { provider.register(config) }
} ?: throw MissingKlaviyoModule("forms")

/**
 * Halt the In-App Forms services and observers,
 * hiding any currently displayed forms and preventing any further forms from being presented.
 *
 * @throws MissingKlaviyoModule if the `com.klaviyo:forms` module is not on the classpath.
 */
@UiThread
fun Klaviyo.unregisterFromInAppForms(): Klaviyo =
    Registry.getOrNull<FormsProvider>()?.let { provider ->
        safeApply { provider.unregister() }
    } ?: throw MissingKlaviyoModule("forms")

/**
 * Deliver a JWT to the active IAF webview.
 *
 * Calls `window.klaviyoIAFSetJWT(token)` in the webview via `evaluateJavascript`.
 * If the webview bridge is not yet ready, the token is queued and delivered after
 * the bridge handshake completes.
 *
 * @param token The JWT string to deliver.
 * @throws MissingKlaviyoModule if the `com.klaviyo:forms` module is not on the classpath.
 */
@UiThread
fun Klaviyo.setJWT(token: String): Klaviyo =
    Registry.getOrNull<FormsProvider>()?.let { provider ->
        safeApply { provider.setJWT(token) }
    } ?: throw MissingKlaviyoModule("forms")

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

    /**
     * Deliver a JWT to the active IAF webview.
     * Java-friendly static method.
     *
     * @param token The JWT string to deliver.
     * @see Klaviyo.setJWT
     */
    @JvmStatic
    @UiThread
    fun setJWT(token: String) {
        Klaviyo.setJWT(token)
    }
}

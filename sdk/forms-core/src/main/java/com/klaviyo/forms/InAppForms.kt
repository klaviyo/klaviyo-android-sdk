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
 * Set a forms preview token to load draft forms instead of your published forms, or pass null
 * (or a blank string) to clear it and return to published forms without restarting the app.
 *
 * A preview token is a short-lived credential generated from the Klaviyo form editor for
 * on-device testing of draft forms. It is not for production use: never ship a preview token in a
 * release build. The token is held in memory only, is never persisted or logged, and is cleared
 * automatically when the preview ends or expires. A malformed token is ignored with a warning and
 * clears any previous token.
 *
 * May be called before or after [registerForInAppForms]. If forms are already loaded,
 * the new token takes effect immediately.
 *
 * @param token The preview token, or null to clear it.
 * @throws MissingKlaviyoModule if the `com.klaviyo:forms` module is not on the classpath.
 * @see InAppFormsConfig.previewToken
 */
fun Klaviyo.setFormsPreviewToken(token: String?): Klaviyo =
    Registry.getOrNull<FormsProvider>()?.let { provider ->
        safeApply { provider.setPreviewToken(token) }
    } ?: throw MissingKlaviyoModule("forms")

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
     * Set or clear the forms preview token.
     * Java-friendly static method.
     *
     * @param token The preview token, or null to clear it.
     * @see Klaviyo.setFormsPreviewToken
     */
    @JvmStatic
    fun setFormsPreviewToken(token: String?) {
        Klaviyo.setFormsPreviewToken(token)
    }

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

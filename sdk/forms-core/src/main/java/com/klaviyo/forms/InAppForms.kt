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
 * Start a draft in-app forms preview session with a preview token from the Klaviyo form editor.
 *
 * Starting a preview is like unregistering and registering again: if forms are registered, the
 * current session is torn down, dismissing any form on screen, and a new session starts that loads
 * draft forms for [token]. If [registerForInAppForms] hasn't been called yet, the token is held and
 * the preview starts when forms register; no forms are shown before then.
 *
 * A preview token is a short-lived credential for on-device testing of draft forms. The token is
 * held in memory only and is never persisted or logged. A malformed token is ignored with a warning
 * and changes nothing. When the preview expires, the token is cleared and published forms return in
 * the next session, such as after the next app launch. [unregisterFromInAppForms] also clears it.
 *
 * @param token The preview token.
 * @throws MissingKlaviyoModule if the `com.klaviyo:forms` module is not on the classpath.
 */
fun Klaviyo.startFormsPreview(token: String): Klaviyo =
    Registry.getOrNull<FormsProvider>()?.let { provider ->
        safeApply { provider.startPreview(token) }
    } ?: throw MissingKlaviyoModule("forms")

/**
 * End the draft in-app forms preview session started by [startFormsPreview]. If forms are
 * registered, the current session is torn down, dismissing any form on screen, and a new session
 * starts that loads published forms.
 *
 * @throws MissingKlaviyoModule if the `com.klaviyo:forms` module is not on the classpath.
 */
fun Klaviyo.endFormsPreview(): Klaviyo =
    Registry.getOrNull<FormsProvider>()?.let { provider ->
        safeApply { provider.endPreview() }
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
     * Start a draft in-app forms preview session.
     * Java-friendly static method.
     *
     * @param token The preview token.
     * @see Klaviyo.startFormsPreview
     */
    @JvmStatic
    fun startFormsPreview(token: String) {
        Klaviyo.startFormsPreview(token)
    }

    /**
     * End the draft in-app forms preview session.
     * Java-friendly static method.
     *
     * @see Klaviyo.endFormsPreview
     */
    @JvmStatic
    fun endFormsPreview() {
        Klaviyo.endFormsPreview()
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

package com.klaviyo.forms

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply

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

package com.klaviyo.forms

import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingModule
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
 */
@UiThread
fun Klaviyo.registerForInAppForms(
    config: InAppFormsConfig = InAppFormsConfig()
): Klaviyo = safeApply {
    val provider = Registry.getOrNull<FormsProvider>()
        ?: throw MissingModule("forms")
    provider.register(config)
}

/**
 * Halt the In-App Forms services and observers,
 * hiding any currently displayed forms and preventing any further forms from being presented.
 */
@UiThread
fun Klaviyo.unregisterFromInAppForms() = safeApply {
    val provider = Registry.getOrNull<FormsProvider>()
        ?: throw MissingModule("forms")
    provider.unregister()
}

/**
 * Resets the In-App Forms listeners with the current configuration.
 */
@UiThread
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
fun Klaviyo.reInitializeInAppForms() = safeApply {
    val provider = Registry.getOrNull<FormsProvider>()
        ?: throw MissingModule("forms")
    provider.reInitialize()
}

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

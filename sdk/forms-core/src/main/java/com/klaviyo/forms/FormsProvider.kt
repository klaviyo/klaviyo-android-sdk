package com.klaviyo.forms

import com.klaviyo.core.utils.AdvancedAPI
import kotlinx.coroutines.flow.StateFlow

/**
 * Service interface for the In-App Forms module.
 *
 * The `forms-core` module provides a no-op stub of this interface.
 * The full `forms` module registers its KlaviyoFormsProvider implementation
 * via a FormsInitProvider ContentProvider at app startup.
 */
interface FormsProvider {
    /**
     * Register for In-App Forms with the given [config].
     * Sets up internal services and initializes the WebView client.
     */
    fun register(config: InAppFormsConfig)

    /**
     * Unregister from In-App Forms, dismissing any visible form
     * and tearing down the WebView client.
     */
    fun unregister()

    /**
     * Whether In-App Forms are currently registered and able to present forms.
     *
     * For internal use only. This should not be used in production applications.
     *
     * @see inAppFormsRegistrationState
     */
    @AdvancedAPI
    val registrationState: StateFlow<Boolean>
}

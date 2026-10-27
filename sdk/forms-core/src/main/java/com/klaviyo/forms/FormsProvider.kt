package com.klaviyo.forms

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
     * Start a forms preview session with [token]. If registered, tears down the current session
     * and starts a new one that loads the preview. Otherwise the token is held for the next
     * [register]. A malformed token changes nothing.
     */
    fun startPreview(token: String)

    /**
     * End the forms preview session. If registered, tears down the current session and starts a
     * new one that loads published forms.
     */
    fun endPreview()
}

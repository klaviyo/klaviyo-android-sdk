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
     * Deliver a JWT to the active IAF webview by calling `window.klaviyoIAFSetJWT`.
     * If the webview bridge is not yet ready, the token will be queued and delivered once the
     * handshake completes.
     *
     * @param token The JWT to deliver to the webview.
     */
    fun setJWT(token: String)
}

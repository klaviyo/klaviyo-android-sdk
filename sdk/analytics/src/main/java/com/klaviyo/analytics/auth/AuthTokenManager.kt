package com.klaviyo.analytics.auth

import kotlinx.coroutines.CoroutineScope

/**
 * Manages the lifecycle of the host-supplied [AuthTokenProvider] and the resulting JWTs used by
 * personalized Klaviyo features.
 *
 * Public for [com.klaviyo.core.Registry] lookup from outside the analytics module (e.g. the forms
 * module's WebView injection layer). Implementation is internal.
 */
interface AuthTokenManager {

    /**
     * Coroutine scope owned by the manager. The public [com.klaviyo.analytics.Klaviyo] API uses
     * this scope to launch fire-and-forget calls to the manager's suspending methods.
     */
    val coroutineScope: CoroutineScope

    /**
     * Replace the registered [AuthTokenProvider] (if any), discard any cached token, and eagerly
     * fetch a fresh token via the new provider.
     */
    suspend fun registerProvider(provider: AuthTokenProvider)

    /**
     * Return a currently-valid JWT, fetching from the registered provider if no cached token is
     * available or the cached token has expired.
     *
     * @throws IllegalStateException if no provider is registered, the provider invokes
     *   [AuthTokenProvider.Callback.onFailure], or the returned token fails validation.
     */
    suspend fun currentToken(): String
}

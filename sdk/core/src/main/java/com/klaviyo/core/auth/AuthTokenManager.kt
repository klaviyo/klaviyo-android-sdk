package com.klaviyo.core.auth

/**
 * Callback invoked whenever the cached auth token is refreshed.
 *
 * Internal for now — the public `Klaviyo.onTokenRefresh` / `offTokenRefresh` surface lands with
 * MAGE-630, at which point this typealias and its parameter type will be promoted as needed.
 */
internal typealias TokenRefreshObserver = (token: ValidatedToken) -> Unit

/**
 * Manages the lifecycle of the host-supplied [AuthTokenProvider] and the resulting JWTs used by
 * personalized Klaviyo features.
 *
 * Public for [com.klaviyo.core.Registry] lookup from outside the analytics module (e.g. the forms
 * module's WebView injection layer). Implementation is internal.
 */
interface AuthTokenManager {

    /**
     * Replace the registered [AuthTokenProvider] (if any), discard any cached token, and
     * asynchronously pre-warm the cache with a fresh token via the new provider.
     *
     * This method returns immediately — provider registration is synchronous; the eager fetch
     * runs fire-and-forget on the manager's internal scope.
     */
    fun registerProvider(provider: AuthTokenProvider)

    /**
     * Return a currently-valid [ValidatedToken], fetching from the registered provider if no
     * cached token is available or the cached token has expired. Callers that only need the raw
     * JWT string should read [ValidatedToken.rawToken]; consumers that benefit from the parsed
     * `exp`/`iat` metadata (e.g. cache-aware short-circuiting, refresh scheduling) read it
     * directly. [ValidatedToken.toString] is redacted, so the wrapper is safer to handle than
     * the raw string.
     *
     * @throws [AuthTokenException.NoProviderRegistered] if no provider has been registered.
     * @throws [AuthTokenException.ValidationFailed] if the returned token fails validation.
     * @throws Throwable whatever error the provider passed to [AuthTokenProvider.Callback.onFailure].
     */
    suspend fun currentToken(): ValidatedToken
}

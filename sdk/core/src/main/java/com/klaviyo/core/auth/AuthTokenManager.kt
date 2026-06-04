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

    companion object {
        /**
         * Timeout budget for proactive / background fetches (registration-time warm-up, scheduled
         * refresh).
         */
        const val BACKGROUND_FETCH_TIMEOUT_MS = 5_000L

        /**
         * Best-effort timeout budget used at form-display time, where latency is user-visible.
         * A timeout here degrades gracefully — the form loads without a token rather than blocking.
         */
        const val INTERACTIVE_FETCH_TIMEOUT_MS = 500L
    }

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
     * Concurrent callers that arrive while a provider fetch is already in-flight share the result
     * of that single fetch rather than each triggering a new provider invocation. Each caller's
     * [timeoutMs] budget is enforced independently — a caller that times out does not cancel the
     * underlying fetch, so a later caller with a larger budget can still receive the result.
     *
     * @param timeoutMs Maximum milliseconds to wait for the provider to return a token. Must be
     *   positive. Defaults to [BACKGROUND_FETCH_TIMEOUT_MS], pass [INTERACTIVE_FETCH_TIMEOUT_MS]
     *   at form-display time for a best-effort, non-blocking fetch.
     * @throws [AuthTokenException.NoProviderRegistered] if no provider has been registered.
     * @throws [AuthTokenException.ValidationFailed] if the returned token fails validation.
     * @throws [AuthTokenException.TimedOut] if the provider does not respond within [timeoutMs].
     * @throws IllegalArgumentException if [timeoutMs] is not positive.
     * @throws Throwable whatever error the provider passed to [AuthTokenProvider.Callback.onFailure].
     */
    suspend fun currentToken(timeoutMs: Long = BACKGROUND_FETCH_TIMEOUT_MS): ValidatedToken
}

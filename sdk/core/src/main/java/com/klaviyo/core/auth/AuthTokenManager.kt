package com.klaviyo.core.auth

/**
 * Callback invoked whenever the auth token is acquired or refreshed — the initial demand fetch,
 * each subsequent proactive refresh, and each replacement for a rejected token.
 *
 * Receives the raw JWT string. Observers must not retain the string beyond their immediate use —
 * for the full [ValidatedToken] wrapper (exp/iat metadata) callers should use
 * [AuthTokenManager.currentToken].
 *
 * Observers are invoked serially on the manager's internal dispatcher (IO). If a thread handoff is needed
 * (e.g. for a WebView call that must run on the UI thread), the observer is responsible for it.
 */
typealias TokenRefreshObserver = (jwt: String) -> Unit

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
     * This method returns after queuing registration. The eager fetch runs asynchronously.
     */
    fun registerProvider(provider: AuthTokenProvider)

    /**
     * Detach the registered [AuthTokenProvider] and tear down all associated token state.
     *
     * Cancels any in-flight token fetch, scheduled proactive refresh, and connectivity-wait job,
     * then clears the cached token and the provider reference. Subsequent calls to [currentToken]
     * will throw [AuthTokenException.NoProviderRegistered] until a new provider is registered via
     * [Klaviyo.registerAuthTokenProvider][com.klaviyo.analytics.Klaviyo.registerAuthTokenProvider].
     *
     * Has no effect if no provider is currently registered. Teardown runs asynchronously.
     */
    fun unregisterProvider()

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
     * @throws [AuthTokenException.ProviderCancelled] if the provider reports a cancellation.
     * @throws IllegalArgumentException if [timeoutMs] is not positive.
     * @throws Throwable whatever error the provider passed to [AuthTokenProvider.Callback.onFailure].
     */
    suspend fun currentToken(timeoutMs: Long = BACKGROUND_FETCH_TIMEOUT_MS): ValidatedToken

    /**
     * Discard the cached token because a server rejected it, cancel the refresh scheduled for that
     * token, then fetch one replacement from the registered provider. The replacement reaches
     * [TokenRefreshObserver]s like any other acquisition and schedules its own refresh.
     *
     * Each call starts at most one provider fetch, and joins a fetch that is already in flight
     * instead of starting another. Does nothing when no provider is registered or a profile reset
     * is pending. The replacement fetch follows the same retry behavior as any other token fetch.
     */
    fun refreshRejectedToken()

    /**
     * Return whether [rawToken] is the raw JWT of the cached token for the active profile.
     * Returns false when no token is cached or the profile was invalidated since it was fetched.
     */
    fun isCurrentToken(rawToken: String): Boolean

    /**
     * Register an observer that will be invoked each time the auth token is acquired or refreshed,
     * including the initial fetch — so a consumer that subscribes while the first fetch is still in
     * flight (e.g. a form displayed before the token resolves) still receives it once it lands.
     *
     * Multiple observers are supported. Each is invoked serially on the manager's internal dispatcher
     * (IO); the observer is responsible for any thread handoff it needs (e.g. hopping to the UI
     * thread for WebView calls). Dispatch is best-effort: if an observer throws an [Exception], it
     * is logged at WARNING and remaining observers are still called. An observer-thrown
     * [kotlinx.coroutines.CancellationException] stops delivery of the current token.
     *
     * Registration is by reference — pass the same lambda instance to [offTokenRefresh] to
     * unregister. Duplicate registrations (same instance) add the observer twice.
     */
    fun onTokenRefresh(observer: TokenRefreshObserver)

    /**
     * Remove a previously registered [TokenRefreshObserver]. The [observer] must be the same
     * instance (by reference) as the one passed to [onTokenRefresh]. Has no effect if the observer
     * is not currently registered.
     */
    fun offTokenRefresh(observer: TokenRefreshObserver)

    /**
     * Synchronously mark the current profile as stale, preventing in-flight refresh results from
     * reaching registered [TokenRefreshObserver]s. Token-state cleanup is queued.
     *
     * @return A generation ID to pass to [clearTokenState] so an older clear cannot finish a
     *   later reset or clear a replacement provider.
     */
    fun invalidate(): Long

    /**
     * Clear all token-acquisition state tied to the current user, called from the analytics
     * `StateSideEffects` observer when a profile identifier changes, the profile is reset, or the
     * company (API key) changes. Discards the cached token, cancels the scheduled proactive refresh
     * and its wall-clock target, and cancels any in-flight fetch. Without a pending [currentToken]
     * caller, the next call to [currentToken] drives acquisition.
     *
     * Retains:
     * - The registered [AuthTokenProvider], which reads the current user on each invocation.
     * - The lifecycle observer, which is idle without a cached token or scheduled refresh.
     * - Registered [TokenRefreshObserver]s, whose stream resumes on the next successful fetch.
     *
     * @param expectedGeneration The ID returned by [invalidate]. The clear is skipped when a
     *   later profile transition superseded that ID. The default performs an unconditional clear.
     *
     * NOTE: This method's behavior may change in a future revision. The current design ("Option B")
     * retains the provider across resets. An alternative ("Option A") would fully unregister the
     * provider on reset, requiring the host to re-register after each login. If we switch to
     * Option A, this method's name and behavior will change.
     */
    suspend fun clearTokenState(expectedGeneration: Long = -1L)
}

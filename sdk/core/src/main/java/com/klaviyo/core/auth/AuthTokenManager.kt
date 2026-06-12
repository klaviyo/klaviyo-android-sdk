package com.klaviyo.core.auth

/**
 * Callback invoked whenever the auth token is proactively refreshed.
 *
 * Receives the raw JWT string. Observers must not retain the string beyond their immediate use —
 * for the full [ValidatedToken] wrapper (exp/iat metadata) callers should use
 * [AuthTokenManager.currentToken].
 *
 * Observers are invoked on the manager's internal dispatcher (IO). If a thread handoff is needed
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

    /**
     * Register an observer that will be invoked each time the auth token is proactively refreshed.
     *
     * Multiple observers are supported. Each is invoked on the manager's internal dispatcher
     * (IO); the observer is responsible for any thread handoff it needs (e.g. hopping to the UI
     * thread for WebView calls). Dispatch is best-effort: if an observer throws an [Exception], it
     * is logged at WARNING and remaining observers are still called.
     * [kotlinx.coroutines.CancellationException] is rethrown per structured-concurrency contract.
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
     * Synchronously mark the current profile as stale, preventing any in-flight proactive refresh
     * from dispatching its result to registered [TokenRefreshObserver]s.
     *
     * This method is intentionally non-suspending so it can be called on any thread (including the
     * main thread from `Klaviyo.resetProfile()`) without blocking. The actual token-state cleanup
     * is deferred to [clearTokenState], which callers should dispatch asynchronously after calling
     * this method.
     *
     * @return The new profile generation value, which should be passed to [clearTokenState] as
     *   [clearTokenState]'s `expectedGeneration` argument. If a new provider is registered before
     *   [clearTokenState] runs, the generation will have advanced and [clearTokenState] will skip
     *   the clear to preserve the new session's state.
     */
    fun invalidate(): Long

    /**
     * Clear all token-acquisition state tied to the current user, called from
     * `Klaviyo.resetProfile()` on logout. Discards the cached token, cancels the scheduled
     * proactive refresh and its wall-clock target, and cancels any in-flight fetch. Does **not**
     * eagerly re-invoke the provider — the next call to [currentToken] drives the next acquisition.
     *
     * Deliberately retains:
     * - The registered [AuthTokenProvider] — it is host integration code ("how to ask my auth
     *   system for a token"), not user identity. The provider is expected to read the current user
     *   fresh on each invocation, so the same provider correctly serves the next identified profile.
     * - The lifecycle observer — safe to leave running across profile resets; its handler is a
     *   no-op when no cached token or scheduled refresh exists.
     * - Registered [TokenRefreshObserver]s — active form displays should keep their subscriptions
     *   alive across a reset; the stream simply goes quiet until the next successful refresh.
     *
     * @param expectedGeneration When non-negative, the clear is skipped if the profile generation
     *   has advanced past this value (indicating a new provider was registered between [invalidate]
     *   and this call). Pass the value returned by [invalidate], or omit / pass `-1` for an
     *   unconditional clear. Callers that do not use [invalidate] should always use the default.
     *
     * NOTE: This method's behavior may change in a future revision. The current design ("Option B")
     * retains the provider across resets. An alternative ("Option A") would fully unregister the
     * provider on reset, requiring the host to re-register after each login. If we switch to
     * Option A, this method's name and behavior will change.
     */
    suspend fun clearTokenState(expectedGeneration: Long = -1L)
}

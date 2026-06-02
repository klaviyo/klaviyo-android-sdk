package com.klaviyo.core.auth

/**
 * Errors thrown by [AuthTokenManager] when an auth-token request cannot be satisfied.
 *
 * SDK modules that consume [AuthTokenManager] (e.g. the forms WebView injection layer) can branch
 * on these cases to distinguish "no provider has been registered yet" from "the provider returned
 * an invalid token" when shaping their fallback behavior.
 *
 * Mirrors iOS `AuthTokenError`.
 */
sealed class AuthTokenException(message: String) : RuntimeException(message) {

    /**
     * [AuthTokenManager.currentToken] was called before any [AuthTokenProvider] was registered.
     * Callers should treat this as "auth is not enabled" rather than "auth failed."
     */
    data object NoProviderRegistered : AuthTokenException("No auth token provider registered")

    /**
     * The provider returned a token that did not pass [JWTParser] validation.
     * The [reason] is the name of the failing [JWTValidationResult] variant.
     */
    data class ValidationFailed(val reason: String) : AuthTokenException(
        "Auth token validation failed: $reason"
    )

    /**
     * The provider did not return a token within the caller's timeout budget.
     * The underlying provider invocation may still be in-flight; a later caller with a longer
     * budget can still receive the result without triggering a second provider call.
     *
     * Mirrors iOS `AuthTokenError.timedOut`.
     */
    data object TimedOut : AuthTokenException("Auth token request timed out")
}

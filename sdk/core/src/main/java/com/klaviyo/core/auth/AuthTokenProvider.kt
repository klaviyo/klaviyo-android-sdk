package com.klaviyo.core.auth

/**
 * Host-app-supplied source of authentication tokens (JWTs) for personalized Klaviyo features.
 *
 * Implementations are invoked by the SDK when a fresh token is required. The host MUST invoke
 * exactly one of [Callback.onSuccess] or [Callback.onFailure] for each call to [fetchToken].
 * The SDK caches the returned token and calls [fetchToken] again only when it needs a new one:
 * before the current token expires, or after a server rejected it. Return a freshly issued token
 * on every call; do not return a token cached from a previous call.
 */
fun interface AuthTokenProvider {
    fun fetchToken(callback: Callback)

    interface Callback {
        fun onSuccess(jwt: String)

        fun onFailure(error: Throwable)
    }
}

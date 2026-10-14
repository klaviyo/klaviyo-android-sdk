package com.klaviyo.analytics

import com.klaviyo.core.auth.AuthTokenProvider as CoreAuthTokenProvider

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

/**
 * Adapts this host-facing provider to the core provider that the auth token manager invokes. Kept
 * here so host apps never need core on their compile classpath.
 */
internal fun AuthTokenProvider.toCore(): CoreAuthTokenProvider =
    CoreAuthTokenProvider { coreCallback ->
        fetchToken(object : AuthTokenProvider.Callback {
            override fun onSuccess(jwt: String) = coreCallback.onSuccess(jwt)

            override fun onFailure(error: Throwable) = coreCallback.onFailure(error)
        })
    }

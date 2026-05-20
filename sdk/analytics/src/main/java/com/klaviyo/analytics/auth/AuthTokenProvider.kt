package com.klaviyo.analytics.auth

/**
 * Host-app-supplied source of authentication tokens (JWTs) for personalized Klaviyo features.
 *
 * Implementations are invoked by the SDK when a fresh token is required. The host MUST invoke
 * exactly one of [Callback.onSuccess] or [Callback.onFailure] for each call to [fetchToken].
 */
fun interface AuthTokenProvider {
    fun fetchToken(callback: Callback)

    interface Callback {
        fun onSuccess(jwt: String)

        fun onFailure(error: Throwable)
    }
}

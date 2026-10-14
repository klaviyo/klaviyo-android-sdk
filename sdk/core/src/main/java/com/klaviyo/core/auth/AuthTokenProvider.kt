package com.klaviyo.core.auth

/**
 * The provider contract that [AuthTokenManager] invokes. Host apps should not use this type. They
 * register a `com.klaviyo.analytics.AuthTokenProvider`, which is adapted to this one.
 */
fun interface AuthTokenProvider {
    fun fetchToken(callback: Callback)

    interface Callback {
        fun onSuccess(jwt: String)

        fun onFailure(error: Throwable)
    }
}

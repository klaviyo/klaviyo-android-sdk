package com.klaviyo.core.auth

/**
 * The provider contract invoked by [AuthTokenManager]. `com.klaviyo.analytics.AuthTokenProvider`
 * is the public equivalent, adapted to this type.
 */
fun interface AuthTokenProvider {
    fun fetchToken(callback: Callback)

    interface Callback {
        fun onSuccess(jwt: String)

        fun onFailure(error: Throwable)
    }
}

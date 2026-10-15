package com.klaviyo.analytics

import com.klaviyo.core.auth.AuthTokenProvider as CoreAuthTokenProvider

/**
 * Supplies JWTs for personalized Klaviyo features. The SDK calls [fetchToken] when it needs a
 * token, and the outcome is reported through [Callback].
 */
fun interface AuthTokenProvider {
    fun fetchToken(callback: Callback)

    interface Callback {
        fun onSuccess(jwt: String)

        fun onFailure(error: Throwable)
    }
}

/** Adapts this provider to the core provider used by the auth token manager. */
@JvmSynthetic
internal fun AuthTokenProvider.toCore(): CoreAuthTokenProvider =
    CoreAuthTokenProvider { coreCallback ->
        fetchToken(object : AuthTokenProvider.Callback {
            override fun onSuccess(jwt: String) = coreCallback.onSuccess(jwt)

            override fun onFailure(error: Throwable) = coreCallback.onFailure(error)
        })
    }

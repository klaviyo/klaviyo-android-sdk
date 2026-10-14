package com.klaviyo.analytics

import com.klaviyo.core.auth.AuthTokenProvider as CoreAuthTokenProvider

/**
 * Supplies JWTs for personalized Klaviyo features. The SDK calls [fetchToken] when it needs a
 * token. Invoke exactly one of [Callback.onSuccess] or [Callback.onFailure] per call, and return a
 * freshly issued token each time.
 */
fun interface AuthTokenProvider {
    fun fetchToken(callback: Callback)

    interface Callback {
        fun onSuccess(jwt: String)

        fun onFailure(error: Throwable)
    }
}

/** Adapts this provider to the core one, so hosts never need core on their classpath. */
@JvmSynthetic
internal fun AuthTokenProvider.toCore(): CoreAuthTokenProvider =
    CoreAuthTokenProvider { coreCallback ->
        fetchToken(object : AuthTokenProvider.Callback {
            override fun onSuccess(jwt: String) = coreCallback.onSuccess(jwt)

            override fun onFailure(error: Throwable) = coreCallback.onFailure(error)
        })
    }

package com.klaviyo.analytics.state

import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.safeLaunch
import kotlinx.coroutines.CoroutineScope

/**
 * Synchronously invalidate cached auth token state, run [block], then queue the token-state clear.
 * The clear is queued even if [block] throws. The registered provider is retained.
 */
internal inline fun AuthTokenManager.resetTokenState(block: () -> Unit) {
    val generation = invalidate()
    try {
        block()
    } finally {
        CoroutineScope(Registry.dispatcher).safeLaunch {
            clearTokenState(expectedGeneration = generation)
        }
    }
}

package com.klaviyo.analytics.state

import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.safeLaunch
import kotlinx.coroutines.CoroutineScope

/**
 * Synchronously invalidate cached auth token state, then queue the token-state clear matched to
 * the generation returned by [AuthTokenManager.invalidate]. The registered provider is retained.
 */
internal fun AuthTokenManager.resetTokenState() {
    val generation = invalidate()
    CoroutineScope(Registry.dispatcher).safeLaunch {
        clearTokenState(expectedGeneration = generation)
    }
}

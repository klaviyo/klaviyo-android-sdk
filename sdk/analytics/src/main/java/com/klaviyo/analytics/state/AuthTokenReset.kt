package com.klaviyo.analytics.state

import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.safeLaunch
import kotlinx.coroutines.CoroutineScope

/**
 * Synchronously invalidate cached auth token state, run [beforeClear], then queue the token-state
 * clear matched to the generation returned by [AuthTokenManager.invalidate]. Commands that
 * [beforeClear] posts to the manager are processed before that clear. The registered provider is
 * retained.
 */
internal fun AuthTokenManager.resetTokenState(beforeClear: () -> Unit = {}) {
    val generation = invalidate()
    beforeClear()
    CoroutineScope(Registry.dispatcher).safeLaunch {
        clearTokenState(expectedGeneration = generation)
    }
}

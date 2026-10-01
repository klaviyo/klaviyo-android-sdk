package com.klaviyo.core.auth

/**
 * A [KlaviyoAuthTokenManager] whose active profile is already identified, so the provider is invoked
 */
internal fun identifiedAuthTokenManager(): KlaviyoAuthTokenManager =
    KlaviyoAuthTokenManager().also { it.setIdentified(true) }

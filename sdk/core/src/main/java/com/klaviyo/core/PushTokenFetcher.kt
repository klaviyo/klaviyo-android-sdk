package com.klaviyo.core

/**
 * Pulls the current push token from the device's push provider (e.g. FCM) and forwards it to
 * Klaviyo. Implemented in `push-fcm` and resolved lazily via [Registry]; when that module is absent
 * the lookup is null, so automatic token registration is a no-op for analytics-only integrators.
 */
interface PushTokenFetcher {
    /**
     * Pull the current push token and forward it to Klaviyo. Must not throw — failures (e.g.
     * Firebase not configured) are logged and swallowed.
     */
    fun fetchAndSetPushToken()
}

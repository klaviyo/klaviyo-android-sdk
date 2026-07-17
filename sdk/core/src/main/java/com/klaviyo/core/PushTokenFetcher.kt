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

/**
 * Whether the SDK's automatic push-token forwarding is enabled, via
 * [Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING]. Manifest opt-OUT: absent → `true`.
 *
 * Single source of truth for the flag key and its default, shared by
 * `Klaviyo.maybeAutoRegisterPushToken` (analytics) and `KlaviyoPushService.onNewToken` (push-fcm)
 * so the two automatic-collection paths can't drift. Lives in core because push-fcm and analytics
 * are separate modules and both must reach it.
 *
 * Independent of automatic open tracking. Note `SdkFeatures` telemetry reads the same manifest key
 * with its own default of `false` (only when the host explicitly declares it) and is unaffected.
 */
fun isAutomaticPushTokenForwardingEnabled(): Boolean =
    Registry.config.getManifestBoolean(Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING, true)

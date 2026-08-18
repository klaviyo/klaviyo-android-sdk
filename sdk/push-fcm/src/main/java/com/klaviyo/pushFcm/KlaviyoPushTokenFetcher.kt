package com.klaviyo.pushFcm

import com.google.firebase.messaging.FirebaseMessaging
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.PushTokenFetcher
import com.klaviyo.core.Registry

/**
 * [PushTokenFetcher] backed by Firebase Cloud Messaging.
 */
internal class KlaviyoPushTokenFetcher : PushTokenFetcher {
    override fun fetchAndSetPushToken(onUnavailable: () -> Unit) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> Klaviyo.setPushToken(token) }
                .addOnFailureListener { e ->
                    Registry.log.warning("Failed to fetch push token for automatic registration", e)
                    // This listener runs after fetchAndSetPushToken has returned, so nothing on the
                    // original call stack can contain a throwing callback — guard it here to honor
                    // the must-not-throw contract no matter what the caller supplied.
                    notifyUnavailable(onUnavailable)
                }
        } catch (e: Exception) {
            // Honor the must-not-throw contract (e.g. getInstance() with no default FirebaseApp)
            Registry.log.warning(
                "Unable to access FirebaseMessaging for automatic push token registration",
                e
            )
            notifyUnavailable(onUnavailable)
        }
    }

    /**
     * Invoke [onUnavailable], containing any failure it raises. Keeps [fetchAndSetPushToken]'s
     * must-not-throw contract intact on the asynchronous path, where the caller's stack — and any
     * guard on it — is already gone by the time the provider reports failure.
     */
    private fun notifyUnavailable(onUnavailable: () -> Unit) = runCatching { onUnavailable() }
        .onFailure { Registry.log.error("Push token unavailable callback failed", it) }
}

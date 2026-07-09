package com.klaviyo.pushFcm

import com.google.firebase.messaging.FirebaseMessaging
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.PushTokenFetcher
import com.klaviyo.core.Registry

/**
 * [PushTokenFetcher] backed by Firebase Cloud Messaging.
 */
internal class KlaviyoPushTokenFetcher : PushTokenFetcher {
    override fun fetchAndSetPushToken() {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> Klaviyo.setPushToken(token) }
                .addOnFailureListener { e ->
                    Registry.log.warning("Failed to fetch push token for automatic registration", e)
                }
        } catch (e: Exception) {
            // Honor the must-not-throw contract (e.g. getInstance() with no default FirebaseApp)
            Registry.log.warning(
                "Unable to access FirebaseMessaging for automatic push token registration",
                e
            )
        }
    }
}

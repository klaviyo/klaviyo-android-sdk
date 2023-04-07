package com.klaviyo.sdktestapp.services

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.pushFcm.KlaviyoPushService

class PushService : KlaviyoPushService() {

    companion object {
        /**
         * Get the current push token from FCM and registers with the Push SDK
         */
        fun setSdkPushToken() {
            FirebaseMessaging.getInstance().token.addOnSuccessListener {
                Klaviyo.setPushToken(it)
            }
        }
    }

    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
    }
}

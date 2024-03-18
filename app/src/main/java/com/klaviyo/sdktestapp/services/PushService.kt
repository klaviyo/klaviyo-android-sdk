package com.klaviyo.sdktestapp.services

import android.content.Context
import android.os.Bundle
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.pushFcm.KlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoPushService
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.title

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

        fun createLocalNotification(context: Context) {
            val localMessage = RemoteMessage(
                Bundle().apply {
                    putString("_k", "fake tracking param")
                    putString("title", "Local Notification")
                    putString("body", "Triggered from app.")
//                    putString("small_icon", "ic_mouse")
//                    putString("color", "#44883s")
//                    putString("image_url", "https://lumiere-a.akamaihd.net/v1/images/character_themuppets_kermit_b77a431b.jpeg")
                }
            )

            localMessage.title

            KlaviyoNotification(localMessage).displayNotification(context)
        }
    }

    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
    }
}

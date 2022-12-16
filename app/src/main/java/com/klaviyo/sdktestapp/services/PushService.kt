package com.klaviyo.sdktestapp.services

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.push.KlaviyoPushService
import com.klaviyo.sdktestapp.BuildConfig

class PushService : FirebaseMessagingService() {
    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)
        KlaviyoPushService.setPushToken(newToken)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val customerProperties = KlaviyoCustomerProperties()
            .also {
                it.setEmail(BuildConfig.DEFAULT_EMAIL)
            }
        KlaviyoPushService.handlePush(message.data, customerProperties)
    }
}

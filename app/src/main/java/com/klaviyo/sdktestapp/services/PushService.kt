package com.klaviyo.sdktestapp.services

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.pushFcm.KlaviyoPushService

class PushService : KlaviyoPushService() {

    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
    }
}

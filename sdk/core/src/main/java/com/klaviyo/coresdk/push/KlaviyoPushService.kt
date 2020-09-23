package com.klaviyo.coresdk.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.klaviyo.coresdk.utils.ConfigFileUtils
import com.klaviyo.coresdk.utils.ConfigKeys

class KlaviyoPushService: FirebaseMessagingService() {
    companion object {
        fun getPushToken(): String {
            return ConfigFileUtils.readValue(ConfigKeys.PUSH_TOKEN_KEY)
        }
    }
    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)

        ConfigFileUtils.writeConfigValue(ConfigKeys.PUSH_TOKEN_KEY, newToken)
    }
}
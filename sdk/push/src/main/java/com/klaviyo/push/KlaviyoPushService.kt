package com.klaviyo.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils

class KlaviyoPushService: FirebaseMessagingService() {
    companion object {
        internal const val PUSH_TOKEN_PREFERENCE_KEY = "PUSH_TOKEN"

        internal const val REQUEST_PUSH_KEY = "\$android_tokens"

        fun getCurrentPushToken(): String {
            return KlaviyoPreferenceUtils.readStringPreference(PUSH_TOKEN_PREFERENCE_KEY) ?: ""
        }
    }

    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)

        val properties = KlaviyoCustomerProperties()
        properties.addAppendedProperty(REQUEST_PUSH_KEY, newToken)
        Klaviyo.identify(properties)

        KlaviyoPreferenceUtils.writeStringPreference(PUSH_TOKEN_PREFERENCE_KEY, newToken)
    }
}
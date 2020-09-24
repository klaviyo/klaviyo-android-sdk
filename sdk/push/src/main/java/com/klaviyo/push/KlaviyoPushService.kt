package com.klaviyo.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils

class KlaviyoPushService: FirebaseMessagingService() {
    companion object {
        internal const val PUSH_TOKEN_PREFERENCE_KEY = "PUSH_TOKEN"

        internal const val APPEND_PROPS_KEY = "\$append"
        internal const val REQUEST_PUSH_KEY = "\$android_tokens"

        fun getCurrentPushToken(): String {
            return KlaviyoPreferenceUtils.readStringPreference(PUSH_TOKEN_PREFERENCE_KEY) ?: ""
        }
    }

    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)

        val appendedPropsMap = mutableMapOf(REQUEST_PUSH_KEY to newToken)
        Klaviyo.identify(mutableMapOf(APPEND_PROPS_KEY to appendedPropsMap))

        KlaviyoPreferenceUtils.writeStringPreference(PUSH_TOKEN_PREFERENCE_KEY, newToken)


    }
}
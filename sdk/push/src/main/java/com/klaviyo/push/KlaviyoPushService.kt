package com.klaviyo.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils

/**
 * Implementation of the FCM messaging service that runs when the parent application is started
 * This service handles all the interaction with FCM service that the SDK needs
 *
 * If the parent application has their own FCM messaging service defined they need to ensure
 * that the implementation details of this service are carried over into their own
 */
class KlaviyoPushService : FirebaseMessagingService() {
    companion object {
        internal const val PUSH_TOKEN_PREFERENCE_KEY = "PUSH_TOKEN"

        internal const val REQUEST_PUSH_KEY = "\$android_tokens"

        /**
         * Returns the current push token that we have stored on this device
         *
         * @return The push token we read from the shared preferences
         */
        fun getCurrentPushToken(): String {
            return KlaviyoPreferenceUtils.readStringPreference(PUSH_TOKEN_PREFERENCE_KEY) ?: ""
        }
    }

    /**
     * FCM service calls this function whenever a token is generated
     * This can be whenever a token is created anew, or whenever it has expired and regenerated itself
     *
     * We append this new token to a property map and queue it into an identify request to send to
     * the Klaviyo asynchronous APIs.
     * We then write it into the shared preferences so that we can fetch the token for this device
     * as needed
     *
     * @param newToken The newly generated token returned from the FCM service
     */
    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)

        val properties = KlaviyoCustomerProperties()
            .addAppendProperty(REQUEST_PUSH_KEY, newToken)
        Klaviyo.identify(properties)

        KlaviyoPreferenceUtils.writeStringPreference(PUSH_TOKEN_PREFERENCE_KEY, newToken)
    }
}

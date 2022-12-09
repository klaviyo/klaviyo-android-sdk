package com.klaviyo.push

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.networking.KlaviyoEvent
import com.klaviyo.coresdk.networking.KlaviyoEventProperties
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

        private const val REQUEST_PUSH_KEY = "\$android_tokens"

        fun setPushToken(pushToken: String) {
            val properties = KlaviyoCustomerProperties()
                .addAppendProperty(REQUEST_PUSH_KEY, pushToken)

            Klaviyo.identify(properties)

            KlaviyoPreferenceUtils.writeStringPreference(PUSH_TOKEN_PREFERENCE_KEY, pushToken)
        }

        /**
         * Returns the current push token that we have stored on this device
         *
         * @return The push token we read from the shared preferences
         */
        fun getCurrentPushToken(): String {
            return KlaviyoPreferenceUtils.readStringPreference(PUSH_TOKEN_PREFERENCE_KEY) ?: ""
        }

        /**
         * Track an opened push event to klaviyo
         *
         * TODO customer and event properties should both be optional, once SDK is tracking customer state internally
         *
         * @param pushPayload The data attribute of the push notification payload
         */
        fun handlePush(
            pushPayload: Map<String, String>,
            customerProperties: KlaviyoCustomerProperties,
            eventProperties: KlaviyoEventProperties? = null
        ) {
            val properties = eventProperties ?: KlaviyoEventProperties()
            properties.addCustomProperty("push_token", getCurrentPushToken())
            pushPayload.forEach { (key, value) ->
                properties.addCustomProperty(key, value)
            }
            Klaviyo.track(KlaviyoEvent.OPENED_PUSH, customerProperties, properties)
        }

        /**
         * Track an opened push event to klaviyo
         *
         * @param notificationIntent The Intent generated from the notification
         */
        fun handlePush(
            notificationIntent: Intent?,
            customerProperties: KlaviyoCustomerProperties,
            eventProperties: KlaviyoEventProperties? = null
        ) {
            if (notificationIntent?.getStringExtra("origin") == "klaviyo") {
                val payload = mutableMapOf<String, String>()

                notificationIntent.extras?.keySet()?.forEach { key ->
                    payload[key] = notificationIntent.getStringExtra(key) ?: ""
                }

                handlePush(payload, customerProperties, eventProperties)
            }
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
        setPushToken(newToken)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        handlePush(message.data, KlaviyoCustomerProperties())
    }
}

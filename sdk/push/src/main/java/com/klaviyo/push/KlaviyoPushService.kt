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

        /**
         * Save the device FCM push token and register to the current profile
         *
         * We append this token to a property map and queue it into an identify request to send to
         * the Klaviyo asynchronous APIs.
         * We then write it into the shared preferences so that we can fetch the token for this
         * device as needed
         *
         * @see FirebaseMessagingService.onNewToken()
         * @param pushToken The push token provided by the FCM Service
         */
        fun setPushToken(pushToken: String) {
            val properties = KlaviyoCustomerProperties()
                .addAppendProperty(REQUEST_PUSH_KEY, pushToken)

            Klaviyo.setProfile(properties)

            KlaviyoPreferenceUtils.writeStringPreference(PUSH_TOKEN_PREFERENCE_KEY, pushToken)
        }

        /**
         * Retrieve the device FCM push token  have stored on this device
         *
         * @return The push token we read from the shared preferences
         */
        fun getPushToken(): String {
            return KlaviyoPreferenceUtils.readStringPreference(PUSH_TOKEN_PREFERENCE_KEY) ?: ""
        }

        /**
         * Logs an $opened_push event for a remote notification that originated from Klaviyo
         *
         * @param notificationPayload The data attributes of the push notification payload
         * @param customerProperties Profile with which to associate the event
         * @param eventProperties Optional additional properties for the event
         */
        fun openedPush(
            notificationPayload: Map<String, String>,
            customerProperties: KlaviyoCustomerProperties? = null,
            eventProperties: KlaviyoEventProperties? = null
        ) {
            notificationPayload["_k"] ?: return // Track pushes originating from klaviyo
            val properties = eventProperties ?: KlaviyoEventProperties()
            properties.addCustomProperty("push_token", getPushToken())
            notificationPayload.forEach { (k, v) -> properties.addCustomProperty(k, v) }
            Klaviyo.createEvent(KlaviyoEvent.OPENED_PUSH, properties, customerProperties)
        }

        /**
         * Logs an $opened_push event for a remote notification that originated from Klaviyo
         * After being opened from the system tray
         *
         * @param notificationIntent The Intent generated from tapping the notification
         * @param customerProperties Profile with which to associate the event
         * @param eventProperties Optional additional properties for the event
         */
        fun openedPush(
            notificationIntent: Intent?,
            customerProperties: KlaviyoCustomerProperties? = null,
            eventProperties: KlaviyoEventProperties? = null
        ) {
            val extras = notificationIntent?.extras ?: return
            val payload = extras.keySet().associateWith { key ->
                extras.getString(key, "")
            }
            openedPush(payload, customerProperties, eventProperties)
        }
    }

    /**
     * FCM service calls this function whenever a token is generated
     * This can be whenever a token is created anew, or whenever it has expired and regenerated itself
     *
     * Invoke the SDK to log the push notification to the profile
     *
     * @param newToken The newly generated token returned from the FCM service
     */
    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)
        setPushToken(newToken)
    }

    /**
     * FCM calls this when any remote message is received
     * while the app is in the foreground, or if a data message is received
     * while the app is backgrounded.
     *
     * Invoke the SDK to log an event for the received message
     *
     * @param message
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        openedPush(message.data)
    }
}

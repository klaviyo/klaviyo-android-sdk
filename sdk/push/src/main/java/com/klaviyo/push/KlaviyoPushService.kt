package com.klaviyo.push

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.KlaviyoEventType
import com.klaviyo.coresdk.model.Profile

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
        private const val PUSH_TOKEN_EVENT_KEY = "push_token"
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
            val properties = Profile().addAppendProperty(REQUEST_PUSH_KEY, pushToken)

            Klaviyo.setProfile(properties)

            Klaviyo.Registry.dataStore.store(PUSH_TOKEN_PREFERENCE_KEY, pushToken)
        }

        /**
         * Retrieve the device FCM push token  have stored on this device
         *
         * @return The push token we read from the shared preferences
         */
        internal fun getPushToken(): String {
            return Klaviyo.Registry.dataStore.fetch(PUSH_TOKEN_PREFERENCE_KEY) ?: ""
        }

        /**
         * Logs an $opened_push event for a remote notification that originated from Klaviyo
         *
         * @param payload The data attributes of the push notification payload
         */
        internal fun openedPush(payload: Map<String, String>) {
            payload["_k"] ?: return // Track only pushes originating from klaviyo

            val event = Event().also {
                payload.forEach { (k, v) -> it.setProperty(k, v) }
                it.setProperty("push_token", getPushToken())
            }

            Klaviyo.createEvent(KlaviyoEventType.OPENED_PUSH, event)
        }

        /**
         * Handles a push received while app is in the foreground
         *
         * @param message
         */
        fun handlePush(message: RemoteMessage): Boolean {
            message.data["_k"] ?: return false
            // TODO could we display the notification in system tray?
            // NOTE this is where we'd handle a data-only push, when we support those
            return true
        }

        /**
         * Logs an $opened_push event for a notification that originated from Klaviyo
         * After being opened from the system tray
         *
         * @param intent The Intent generated from tapping the notification
         */
        fun handlePush(intent: Intent?) {
            val extras = intent?.extras ?: return
            val payload = extras.keySet().associateWith { key -> extras.getString(key, "") }
            openedPush(payload)
        }
    }

    /**
     * FCM service calls this function whenever a token is generated
     * This can be whenever a token is created anew, or whenever it has expired and regenerated itself
     *
     * Invoke the SDK to log the push token to the profile
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
     * @param message
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        handlePush(message)
    }
}

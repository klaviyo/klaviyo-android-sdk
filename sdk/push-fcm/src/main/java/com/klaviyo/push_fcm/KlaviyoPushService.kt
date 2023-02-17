package com.klaviyo.push_fcm

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.UserInfo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventType
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.Registry

/**
 * Implementation of the FCM messaging service that runs when the parent application is started
 * This service handles all the interaction with FCM service that the SDK needs
 *
 * If the parent application has their own FCM messaging service defined they need to ensure
 * that the implementation details of this service are carried over into their own
 */
class KlaviyoPushService : FirebaseMessagingService() {
    companion object {

        /**
         * Saves the device FCM push token and registers to the current profile
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
            Registry.get<ApiClient>().enqueuePushToken(pushToken, UserInfo.getAsProfile())
            Registry.dataStore.store(EventKey.PUSH_TOKEN.name, pushToken)
        }

        /**
         * Retrieves the device FCM push token stored on this device
         *
         * @return The push token we read from the data store
         */
        internal fun getPushToken(): String? = Registry.dataStore.fetch(EventKey.PUSH_TOKEN.name)

        /**
         * Logs an $opened_push event for a remote notification that originated from Klaviyo
         *
         * @param payload The data attributes of the push notification payload
         */
        internal fun openedPush(payload: Map<String, String>) {
            if (!isKlaviyoPush(payload)) return // Track only pushes originating from klaviyo

            val event = Event(
                EventType.OPENED_PUSH,
                payload.mapKeys {
                    EventKey.CUSTOM(it.key)
                }
            )

            getPushToken()?.let { event[EventKey.PUSH_TOKEN] = it }

            Registry.get<ApiClient>().enqueueEvent(event, UserInfo.getAsProfile())
        }

        /**
         * Handles a push received while app is in the foreground
         *
         * @param message Message received via [FirebaseMessagingService.onMessageReceived]
         */
        fun handlePush(message: RemoteMessage): Boolean {
            // TODO could we display the notification in system tray?
            // NOTE this is where we'd handle a data-only push, when we support those
            return isKlaviyoPush(message.data)
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

        private fun isKlaviyoPush(data: Map<String, String>): Boolean = data.containsKey("_k")
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
     * @param message Remote message that has been received
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        handlePush(message)
    }
}

package com.klaviyo.push_fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.Klaviyo

/**
 * Implementation of the FCM messaging service that runs when the parent application is started
 * This service handles all the interaction with FCM service that the SDK needs
 *
 * If the parent application has their own FCM messaging service defined they need to ensure
 * that the implementation details of this service are carried over into their own
 */
class KlaviyoPushService : FirebaseMessagingService() {

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
        Klaviyo.setPushToken(newToken)
    }

    /**
     * FCM calls this when any remote message is received
     * while the app is in the foreground, or if a data message is received
     * while the app is backgrounded.
     *
     * @param message Remote message that has been received
     */
    override fun onMessageReceived(message: RemoteMessage) {
        // TODO could we provide a way to display the notification in system tray?
        super.onMessageReceived(message)
    }
}

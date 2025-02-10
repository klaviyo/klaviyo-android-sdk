package com.klaviyo.pushFcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoMessage
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoNotification

/**
 * Implementation of the FCM messaging service that runs when the parent application is started
 * This service handles all the interaction with FCM service that the SDK needs
 *
 * If the parent application has their own FCM messaging service defined they need to ensure
 * that the implementation details of this service are carried over into their own
 */
open class KlaviyoPushService : FirebaseMessagingService() {

    companion object {
        const val METADATA_DEFAULT_ICON = "com.klaviyo.push.default_notification_icon"
        const val METADATA_DEFAULT_COLOR = "com.klaviyo.push.default_notification_color"
    }

    /**
     * Called when FCM SDK receives a newly registered token
     *
     * @param newToken
     */
    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)
        Klaviyo.setPushToken(newToken)
    }

    /**
     * This method is invoked from FCM SDK
     *  when a "notification" message is received while the app is in the foreground,
     *  and when a "data" message is received regardless of the app's status
     *
     * Klaviyo message payload is always formatted as a "data" message to retain
     * full control over display logic, so all Klaviyo push messages come through this method.
     *
     * @param message Remote message that has been received
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        if (message.isKlaviyoMessage) {
            if (message.isKlaviyoNotification) {
                KlaviyoNotification(message).displayNotification(this)
            } else {
                // Handle silent push notifications
                onSilentPushMessageReceived(message)
            }
        } else {
            Registry.log.info("Ignoring non-Klaviyo RemoteMessage")
        }
    }

    /**
     * Called when a silent push notification is received.
     *
     * This method is designed to be overridden by subclasses to provide custom handling for silent
     * push notifications. By default, it simply logs the received [RemoteMessage]. Subclasses can
     * override this method to extract key-value pairs, perform background processing, or implement
     * any other custom behavior without requiring a user-visible notification.
     *
     * The default implementation is invoked from the default [onMessageReceived] logic. If you
     * require different behavior for silent pushes, simply override this method in your subclass of
     * [KlaviyoPushService] and implement your own handling logic.
     *
     * @param message The [RemoteMessage] object representing the silent push notification.
     */
    open fun onSilentPushMessageReceived(message: RemoteMessage) {
        Registry.log.info("Received silent push notification RemoteMessage: $message")
    }
}

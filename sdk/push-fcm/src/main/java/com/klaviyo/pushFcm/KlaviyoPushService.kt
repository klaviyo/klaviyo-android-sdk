package com.klaviyo.pushFcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.hasKlaviyoKeyValuePairs
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.intendedSendTime
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoMessage
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.keyValuePairs
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.notificationTag
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.shouldSchedule

/**
 * Implementation of the FCM messaging service that runs when the parent application is started
 * This service handles all the interaction with FCM service that the SDK needs
 *
 * If the parent application has their own FCM messaging service defined they need to ensure
 * that the implementation details of this service are carried over into their own
 *
 * This service supports scheduled notifications through the `intended_send_time` field in the
 * notification payload. When this field is present with a future UTC datetime (ISO formatted string),
 * the notification will be scheduled to appear at that time instead of immediately.
 *
 * The scheduling is handled by WorkManager to ensure reliability across app restarts and
 * uses the notification's tag as a unique identifier. If no tag is provided, a timestamp
 * will be used instead.
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
                onKlaviyoNotificationMessageReceived(message = message)
            }
            if (message.hasKlaviyoKeyValuePairs) {
                onKlaviyoCustomDataMessageReceived(
                    customData = message.keyValuePairs ?: emptyMap(),
                    message = message
                )
            }
        } else {
            Registry.log.info("Ignoring non-Klaviyo RemoteMessage")
        }
    }

    /**
     * Called when a standard Klaviyo push notification message is received.
     *
     * This method is invoked by the default [onMessageReceived] logic when a received [RemoteMessage]
     * qualifies as a standard notification (as determined by [RemoteMessage.isKlaviyoNotification]).
     * The default implementation logs the received message and displays the notification via
     * [KlaviyoNotification.displayNotification]. Subclasses can override this method to customize
     * the handling of standard push notifications, such as modifying the display logic or performing
     * additional processing.
     * * If the notification contains an [intended_send_time] field with a future timestamp,
     * the notification will be scheduled for display at that time instead of showing immediately.
     *
     * @param message The [RemoteMessage] object representing the received push notification.
     */
    open fun onKlaviyoNotificationMessageReceived(message: RemoteMessage) {
        Registry.log.info("Received standard push notification with RemoteMessage: $message")

        // Check if the notification has an intended_send_time in the future
        if (message.shouldSchedule) {
            val tag = message.notificationTag ?: Registry.clock.currentTimeMillis().toString()
            val scheduledTime = message.intendedSendTime?.time

            if (scheduledTime != null) {
                val scheduled = KlaviyoScheduledNotificationWorker.scheduleNotification(
                    context = this,
                    tag = tag,
                    message = message,
                    scheduledTimeMillis = scheduledTime
                )

                if (scheduled) {
                    Registry.log.info(
                        "Scheduled notification with tag: $tag for time: ${message.intendedSendTime}"
                    )
                    return
                }

                // If scheduling fails, fall back to immediate display
                Registry.log.warning("Failed to schedule notification, displaying immediately")
            }
        }

        // Display immediately if no scheduling needed or scheduling failed
        KlaviyoNotification(message).displayNotification(this)
    }

    /**
     * Called when a Klaviyo push message containing custom key-value pairs is received.
     *
     * This method is designed to be overridden by subclasses to provide custom handling for
     * Klaviyo messages that include additional custom data. By default, it logs the received
     * custom key-value pairs along with the [RemoteMessage]. Subclasses can override this method to
     * process the custom data, perform background operations, or implement any other behavior tailored
     * to the additional information.
     *
     * The default implementation is invoked from the [onMessageReceived] logic when a Klaviyo message
     * includes key-value pairs. If you require different behavior for handling such messages, simply
     * override this method in your subclass of [KlaviyoPushService] and implement your custom logic.
     *
     * @param customData A [Map] of custom key-value pairs extracted from the Klaviyo message.
     * @param message The [RemoteMessage] object representing the received push notification.
     */
    open fun onKlaviyoCustomDataMessageReceived(
        customData: Map<String, String>,
        message: RemoteMessage
    ) {
        Registry.log.info(
            "Received push notification with custom data: $customData, for RemoteMessage: $message"
        )
    }
}

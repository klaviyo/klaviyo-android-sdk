package com.klaviyo.pushFcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Registry

/**
 * Broadcast receiver that handles device reboots and reschedules pending notifications
 * * This receiver is triggered when the device completes booting and ensures any
 * scheduled notifications that were persisted are re-scheduled with WorkManager.
 */
class KlaviyoBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Only process BOOT_COMPLETED actions
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Registry.log.info("Device boot completed, restoring scheduled notifications")

        // Get all stored notifications
        val notifications = KlaviyoScheduledNotification.getAllNotifications()

        if (notifications.isEmpty()) {
            Registry.log.info("No scheduled notifications to restore")
            return
        }

        Registry.log.info("Restoring ${notifications.size} scheduled notifications")

        // Reschedule each notification
        notifications.forEach { (tag, notification) ->
            // Ensure we don't try to schedule notifications in the past
            if (notification.scheduledTimeMillis > Registry.clock.currentTimeMillis()) {
                // Create a RemoteMessage from the stored data
                val message = RemoteMessage.Builder("scheduled@klaviyo.com").apply {
                    notification.data.forEach { (key, value) ->
                        addData(key, value)
                    }
                }.build()

                // Re-schedule the notification
                KlaviyoScheduledNotificationWorker.scheduleNotification(
                    context = context,
                    tag = tag,
                    message = message,
                    scheduledTimeMillis = notification.scheduledTimeMillis
                )

                Registry.log.info("Restored scheduled notification with tag: $tag")
            } else {
                // If the notification should have been displayed already, remove it
                KlaviyoScheduledNotification.removeNotification(tag)
                Registry.log.info("Removed expired scheduled notification with tag: $tag")
            }
        }
    }
}

package com.klaviyo.pushFcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Registry

/**
 * BroadcastReceiver that handles displaying scheduled notifications when their alarm triggers
 */
class KlaviyoAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_NOTIFICATION_TAG = "notification_tag"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val tag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG) ?: return

        Registry.log.info("Alarm triggered for notification with tag: $tag")

        // Get notification data
        val notificationData = KlaviyoScheduledNotification.getNotification(tag)

        if (notificationData == null) {
            Registry.log.error("Failed to retrieve notification data for tag: $tag")
            return
        }

        // Create RemoteMessage from stored data
        val message = RemoteMessage.Builder("scheduled@klaviyo.com").apply {
            notificationData.data.forEach { (key, value) ->
                addData(key, value)
            }
        }.build()

        // Display the notification
        val notification = KlaviyoNotification(message)
        val displayed = notification.displayNotification(context)

        // Clean up storage
        KlaviyoScheduledNotification.removeNotification(tag)

        Registry.log.info("Displayed scheduled notification with tag: $tag, success: $displayed")
    }
}

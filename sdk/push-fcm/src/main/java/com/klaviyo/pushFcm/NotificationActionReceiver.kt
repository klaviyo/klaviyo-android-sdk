package com.klaviyo.pushFcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry

/**
 * BroadcastReceiver to handle notification action buttons that don't open the app
 *
 * This receiver automatically handles reply and background data actions from push notifications.
 * SDK consumers don't need to implement their own receiver - this is registered automatically.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        internal const val ACTION_SEND_DATA = "com.klaviyo.push.ACTION_SEND_DATA"
        internal const val ACTION_REPLY = "com.klaviyo.push.ACTION_REPLY"
        internal const val EXTRA_NOTIFICATION_ID = "notification_id"
        internal const val EXTRA_NOTIFICATION_TAG = "notification_tag"
        internal const val EXTRA_DATA_PAYLOAD = "data_payload"
        internal const val EXTRA_BUTTON_INDEX = "button_index"
        internal const val EXTRA_BUTTON_TEXT = "button_text"
        internal const val KEY_REPLY_TEXT = "key_reply_text"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Registry.log.info("NotificationActionReceiver: Received action ${intent.action}")

        when (intent.action) {
            ACTION_SEND_DATA -> handleSendData(context, intent)
            ACTION_REPLY -> handleReply(context, intent)
        }
    }

    private fun handleSendData(context: Context, intent: Intent) {
        val payload = intent.getStringExtra(EXTRA_DATA_PAYLOAD) ?: "No data"
        val buttonIndex = intent.getIntExtra(EXTRA_BUTTON_INDEX, -1)
        val buttonText = intent.getStringExtra(EXTRA_BUTTON_TEXT) ?: "Unknown"

        Registry.log.info(
            "Background data action processed: button=$buttonIndex, text=$buttonText, payload=$payload"
        )

        // Track as a Klaviyo custom event
        val event = com.klaviyo.analytics.model.Event("Push Notification Background Action")
            .setProperty("button_index", buttonIndex.toString())
            .setProperty("button_text", buttonText)
            .setProperty("payload", payload)
            .setProperty("timestamp", System.currentTimeMillis().toString())

        Klaviyo.createEvent(event)

        // Dismiss the notification
        dismissNotification(context, intent)
    }

    private fun handleReply(context: Context, intent: Intent) {
        // Extract the reply text from RemoteInput
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(KEY_REPLY_TEXT)?.toString()

        if (replyText.isNullOrBlank()) {
            Registry.log.warning("Reply action received but no text entered")
            return
        }

        val buttonIndex = intent.getIntExtra(EXTRA_BUTTON_INDEX, -1)
        val buttonText = intent.getStringExtra(EXTRA_BUTTON_TEXT) ?: "Unknown"

        Registry.log.info(
            "Reply action processed: button=$buttonIndex, text=$buttonText, reply=$replyText"
        )

        // Track as a Klaviyo custom event
        val event = com.klaviyo.analytics.model.Event("Push Notification Reply")
            .setProperty("button_index", buttonIndex.toString())
            .setProperty("button_text", buttonText)
            .setProperty("reply_text", replyText)
            .setProperty("timestamp", System.currentTimeMillis().toString())

        Klaviyo.createEvent(event)

        // Dismiss the notification
        dismissNotification(context, intent)
    }

    private fun dismissNotification(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val notificationTag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG)

        NotificationManagerCompat.from(context).cancel(notificationTag, notificationId)
        Registry.log.verbose("Dismissed notification: tag=$notificationTag, id=$notificationId")
    }
}

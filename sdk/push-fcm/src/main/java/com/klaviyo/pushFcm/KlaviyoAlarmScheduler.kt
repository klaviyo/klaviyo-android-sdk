package com.klaviyo.pushFcm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.AdvancedAPI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Uses AlarmManager to schedule notifications for exact timing
 */
object KlaviyoAlarmScheduler {

    /**
     * Helper method for sample app to test scheduled notifications
     * Simplifies testing by not requiring instantiation of KlaviyoPushService
     * * @param context Application context
     * @param title Notification title
     * @param body Notification body text
     * @param scheduledTimeMillis The time at which to display the notification in milliseconds
     * @return Whether scheduling was successful
     */
    @JvmStatic
    @OptIn(AdvancedAPI::class)
    fun scheduleTestNotification(
        context: Context,
        title: String,
        body: String,
        scheduledTimeMillis: Long
    ): Boolean {
        try {
            // Format the time as ISO UTC datetime string for storage
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val intendedSendTime = dateFormat.format(Date(scheduledTimeMillis))

            // Generate a unique tag for the notification
            val tag = "test-" + UUID.randomUUID().toString()

            // Create RemoteMessage with test data
            val message = KlaviyoNotification.buildRemoteMessage(
                title = title,
                body = body,
                intendedSendTime = intendedSendTime,
                notificationTag = tag
            )

            // Schedule using our regular scheduling method
            return scheduleNotification(
                context = context,
                tag = tag,
                message = message,
                scheduledTimeMillis = scheduledTimeMillis
            )
        } catch (e: Exception) {
            Registry.log.error("Failed to schedule test notification", e)
            return false
        }
    }

    private const val REQUEST_CODE_PREFIX = 1000

    /**
     * Schedule a notification to be displayed at a specific time using AlarmManager for precise timing
     *
     * @param context Application context
     * @param tag Unique identifier for the notification
     * @param message RemoteMessage containing the notification data
     * @param scheduledTimeMillis The time at which to display the notification
     * @return Whether scheduling was successful
     */
    fun scheduleNotification(
        context: Context,
        tag: String,
        message: RemoteMessage,
        scheduledTimeMillis: Long
    ): Boolean {
        try {
            // Store notification data
            if (!KlaviyoScheduledNotification.storeNotification(
                    tag,
                    message.data,
                    scheduledTimeMillis
                )
            ) {
                return false
            }

            // Calculate delay from current time to scheduled time
            val currentTimeMillis = Registry.clock.currentTimeMillis()
            val delayMillis = scheduledTimeMillis - currentTimeMillis

            // Sanity check - don't schedule if time is in the past
            if (delayMillis <= 0) {
                Registry.log.warning(
                    "Cannot schedule notification in the past: $scheduledTimeMillis vs $currentTimeMillis"
                )
                return false
            }

            // Create the intent for the alarm
            val intent = Intent(context, KlaviyoAlarmReceiver::class.java).apply {
                putExtra(KlaviyoAlarmReceiver.EXTRA_NOTIFICATION_TAG, tag)
            }

            // Generate a unique request code for this notification
            val requestCode = getRequestCode(tag)

            // Set up the pending intent
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Get alarm manager and schedule the notification
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Use the most precise alarm type available for the Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()
            ) {
                // Use setExactAndAllowWhileIdle for API 31+ when permission granted
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledTimeMillis,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use setExactAndAllowWhileIdle for API 23+ (M)
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledTimeMillis,
                    pendingIntent
                )
            } else {
                // Use setExact for API 19+ (KitKat)
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    scheduledTimeMillis,
                    pendingIntent
                )
            }

            Registry.log.info(
                "Scheduled notification with AlarmManager. Tag: $tag, Time: $scheduledTimeMillis"
            )
            return true
        } catch (e: Exception) {
            Registry.log.error("Failed to schedule notification with AlarmManager", e)
            return false
        }
    }

    /**
     * Cancel a scheduled notification
     *
     * @param context Application context
     * @param tag Unique identifier for the notification
     */
    fun cancelScheduledNotification(context: Context, tag: String) {
        try {
            // Create the intent that matches the one used to schedule
            val intent = Intent(context, KlaviyoAlarmReceiver::class.java).apply {
                putExtra(KlaviyoAlarmReceiver.EXTRA_NOTIFICATION_TAG, tag)
            }

            // Get the request code for this tag
            val requestCode = getRequestCode(tag)

            // Create matching pending intent
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Get alarm manager and cancel the alarm
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)

            // Remove from storage
            KlaviyoScheduledNotification.removeNotification(tag)

            Registry.log.info("Cancelled scheduled notification with tag: $tag")
        } catch (e: Exception) {
            Registry.log.warning("Failed to cancel scheduled notification", e)
        }
    }

    /**
     * Generate a unique request code for a notification tag
     * This ensures each notification gets a unique pending intent
     */
    private fun getRequestCode(tag: String): Int {
        return REQUEST_CODE_PREFIX + tag.hashCode()
    }
}

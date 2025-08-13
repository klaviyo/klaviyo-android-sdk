package com.klaviyo.pushFcm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Registry
import java.util.concurrent.TimeUnit

/**
 * Worker to display scheduled notifications at the intended time
 */
class KlaviyoScheduledNotificationWorker(
    private val appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    // Create a notification channel ID for the foreground service notification
    private val NOTIFICATION_CHANNEL_ID = "klaviyo_scheduled_notifications_channel"
    private val FOREGROUND_NOTIFICATION_ID = 1337

    /**
     * Set up foreground info for the worker to help ensure timely execution
     * This is required for expedited work in newer Android versions
     */
    override fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            FOREGROUND_NOTIFICATION_ID,
            createForegroundNotification()
        )
    }

    /**
     * Create a silent notification for the foreground service
     * This is required for the worker to run with higher priority
     */
    private fun createForegroundNotification(): Notification {
        // Create notification channel if needed (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Scheduled Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for scheduling Klaviyo notifications"
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Create a silent notification for the foreground service
        return NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Scheduling notifications")
            .setContentText("Ensuring your notifications arrive on time")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null)
            .setVibrate(null)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG_PREFIX = "klaviyo_scheduled_notification_"
        private const val KEY_NOTIFICATION_TAG = "notification_tag"

        /**
         * Schedule a notification to be displayed at a specific time
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

            // Store notification data for later retrieval
            if (!KlaviyoScheduledNotification.storeNotification(
                    tag,
                    message.data,
                    scheduledTimeMillis
                )
            ) {
                return false
            }

            // Create work request with the notification tag as input data
            val inputData = Data.Builder()
                .putString(KEY_NOTIFICATION_TAG, tag)
                .build()

            // Create constraints to ensure the work runs even when app is backgrounded
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // We can't use expedited work with a delay
            val workRequest = OneTimeWorkRequestBuilder<KlaviyoScheduledNotificationWorker>()
                .setInputData(inputData)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, // minimum backoff delay in seconds
                    TimeUnit.SECONDS
                )
                .addTag(getWorkTag(tag))
                .build()

            // Schedule the work
            WorkManager.getInstance(context).enqueue(workRequest)

            Registry.log.info(
                "Scheduled notification with tag: $tag for time: $scheduledTimeMillis"
            )
            return true
        }

        /**
         * Cancel a scheduled notification
         *
         * @param context Application context
         * @param tag Unique identifier for the notification
         */
        fun cancelScheduledNotification(context: Context, tag: String) {
            WorkManager.getInstance(context).cancelAllWorkByTag(getWorkTag(tag))
            KlaviyoScheduledNotification.removeNotification(tag)
            Registry.log.info("Cancelled scheduled notification with tag: $tag")
        }

        /**
         * Get work tag for a notification
         */
        private fun getWorkTag(tag: String) = "$TAG_PREFIX$tag"
    }

    override fun doWork(): Result {
        // Get notification tag from input data
        val tag = inputData.getString(KEY_NOTIFICATION_TAG)

        if (tag == null) {
            Registry.log.error("Missing notification tag in worker input data")
            return Result.failure()
        }

        // Retrieve stored notification data
        val notificationData = KlaviyoScheduledNotification.getNotification(tag)

        if (notificationData == null) {
            Registry.log.error("Failed to retrieve notification data for tag: $tag")
            return Result.failure()
        }

        // Create RemoteMessage from stored data
        val message = RemoteMessage.Builder("scheduled@klaviyo.com").apply {
            notificationData.data.forEach { (key, value) ->
                addData(key, value)
            }
        }.build()

        // Display the notification
        val notification = KlaviyoNotification(message)
        val displayed = notification.displayNotification(appContext)

        // Clean up storage
        KlaviyoScheduledNotification.removeNotification(tag)

        return if (displayed) Result.success() else Result.failure()
    }
}

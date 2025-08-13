package com.klaviyo.pushFcm

import android.content.Context
import androidx.work.Data
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

            val workRequest = OneTimeWorkRequestBuilder<KlaviyoScheduledNotificationWorker>()
                .setInputData(inputData)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
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

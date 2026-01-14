package com.klaviyo.analytics.networking

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.R
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config

/**
 * WorkManager worker that triggers API queue flush
 *
 * This worker is scheduled when network requests need to be sent but
 * immediate execution isn't possible (e.g., during Doze mode).
 *
 * WorkManager ensures this executes when:
 * - Network connectivity is available
 * - Device exits Doze mode or enters maintenance window
 *
 * Using CoroutineWorker for consistency with future coroutine migration plans.
 *
 * Note: This worker uses expedited work which may run as a foreground service
 * on Android 11 and below. The getForegroundInfo() method is required for this.
 */
internal class QueueFlushWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    /**
     * Provide foreground service notification info for expedited work
     *
     * Required when using setExpedited() to support Android 11 and below.
     * On Android 12+, expedited jobs don't run as foreground services,
     * so this notification won't be shown.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo = ForegroundInfo(
        BG_SYNC_NOTIFICATION_ID,
        buildSyncNotification(context)
    )

    /**
     * Perform the queue flush operation
     *
     * This method is called by WorkManager when all constraints are satisfied.
     * It triggers the existing queue flush logic which will process all
     * pending requests, not just specific types of events.
     *
     * @return Result.success() to indicate work completed successfully
     */
    override suspend fun doWork(): Result {
        try {
            Registry.log.verbose("WorkManager triggered queue flush")

            if (!Registry.isRegistered<Config>() || !Registry.isRegistered<ApiClient>()) {
                // Initializes dependencies for access to data store and API Client
                Klaviyo.registerForLifecycleCallbacks(context)
            }

            Registry.get<ApiClient>().run {
                // Restore queue from disk, if it hasn't already been
                restoreQueue()

                // Flush queue and await outcome of all requests
                val outcome = awaitFlushQueueOutcome()
                Registry.log.debug("WorkManager queue flush $outcome")
            }
        } catch (e: Exception) {
            // Return success - we don't want to repeatedly retry, just wait till next opportunity
            Registry.log.error("WorkManager queue flush failed", e)
        }

        return Result.success()
    }

    private companion object {
        /**
         * Notification channel for SDK background operations
         */
        const val BG_SYNC_CHANNEL_ID = "klaviyo_sdk_background"
        const val BG_SYNC_NOTIFICATION_ID = 1001

        /**
         * Build notification for background sync foreground service
         * Note: Only used for APIs 23-30 for expedited work while the app is backgrounded, due to
         * Android limitations. These API levels do not require user-granted notification permission
         */
        fun buildSyncNotification(context: Context): Notification = NotificationCompat
            .Builder(context, createNotificationChannel(context))
            .setContentText(context.getString(R.string.klaviyo_bg_sync_notification_text))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(true)
            .build()

        /**
         * Create notification channel for background sync notifications
         * @return The channel ID
         */
        fun createNotificationChannel(context: Context): String = NotificationManagerCompat
            .from(context)
            .createNotificationChannel(
                NotificationChannelCompat.Builder(BG_SYNC_CHANNEL_ID, IMPORTANCE_LOW)
                    .setName(context.getString(R.string.klaviyo_bg_sync_channel_name))
                    .setDescription(context.getString(R.string.klaviyo_bg_sync_channel_description))
                    .setShowBadge(false)
                    .build()
            ).let { BG_SYNC_CHANNEL_ID }
    }
}

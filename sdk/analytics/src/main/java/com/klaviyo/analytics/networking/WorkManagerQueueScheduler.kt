package com.klaviyo.analytics.networking

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.klaviyo.core.Registry

/**
 * WorkManager-based implementation of QueueScheduler
 *
 * Uses Android WorkManager to schedule queue flush operations that respect
 * system constraints including network availability and Doze mode restrictions.
 *
 * When the device is in Doze mode, WorkManager will execute the work during
 * maintenance windows (approximately every 30-60 minutes), ensuring that
 * queued requests are eventually sent even when the app is backgrounded.
 */
internal class WorkManagerQueueScheduler(
    private val applicationContext: Context
) : QueueScheduler {

    companion object {
        /**
         * Unique name for the queue flush work
         * Using KEEP policy ensures we don't schedule duplicate work
         */
        private const val WORK_NAME = "klaviyo_queue_flush"
    }

    /**
     * Schedule a queue flush using WorkManager
     *
     * Creates an expedited one-time work request with network connectivity constraint.
     * - If network is available: executes immediately
     * - If in Doze mode: executes during next maintenance window (~30-60 min)
     * - Uses KEEP policy to prevent duplicate scheduling
     */
    override fun scheduleFlush() {
        val workRequest = OneTimeWorkRequestBuilder<QueueFlushWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            // Expedited work runs immediately when possible, or during maintenance windows in Doze
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP, // Don't schedule if already pending
                workRequest
            )

        Registry.log.verbose("Scheduled WorkManager queue flush")
    }

    /**
     * Cancel any pending queue flush work
     */
    override fun cancelScheduledFlush() {
        WorkManager.getInstance(applicationContext)
            .cancelUniqueWork(WORK_NAME)

        Registry.log.verbose("Cancelled WorkManager queue flush")
    }
}

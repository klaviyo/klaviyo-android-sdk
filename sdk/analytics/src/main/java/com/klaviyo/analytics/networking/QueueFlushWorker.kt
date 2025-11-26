package com.klaviyo.analytics.networking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.klaviyo.analytics.Klaviyo
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
 */
internal class QueueFlushWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

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
        Registry.log.info("WorkManager triggered queue flush")

        try {
            if (!Registry.isRegistered<Config>()) {
                // Initialize Klaviyo with config to access dataStore
                Klaviyo.registerForLifecycleCallbacks(context)
            }

            // Restore queue from persistent store, if needed, and flush immediately
            KlaviyoApiClient.run {
                restoreQueue()
                flushQueue()
            }
        } catch (e: Exception) {
            Registry.log.error("WorkManager queue flush failed", e)
            // Return success anyway - we don't want WorkManager to retry
            // The queue will be flushed on next opportunity
        }

        return Result.success()
    }
}

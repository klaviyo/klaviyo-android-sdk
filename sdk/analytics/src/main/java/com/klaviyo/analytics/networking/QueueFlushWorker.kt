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
        try {
            Registry.log.info("WorkManager triggered queue flush")

            if (!Registry.isRegistered<Config>() || !Registry.isRegistered<ApiClient>()) {
                // Initializes dependencies for access to data store and API Client
                Klaviyo.registerForLifecycleCallbacks(context)
            }

            Registry.get<ApiClient>().run {
                // Restore queue from disk, if it hasn't already been
                startService()

                // Flush queue will start sending queued requests immediately
                flushQueue()
            }
        } catch (e: Exception) {
            // Return success - we don't want to repeatedly retry, just wait till next opportunity
            Registry.log.error("WorkManager queue flush failed", e)
        }

        return Result.success()
    }
}

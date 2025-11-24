package com.klaviyo.analytics.networking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.klaviyo.core.Registry

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
    context: Context,
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
            // Access KlaviyoApiClient directly - we know it's in the same module
            // If not initialized, we can initialize it since we're in analytics module
            val apiClient = Registry.getOrNull<ApiClient>()
                ?: run {
                    // If ApiClient not registered, ensure it's initialized
                    // This can happen after process death
                    if (!Registry.isRegistered<ApiClient>()) {
                        Registry.register<ApiClient>(KlaviyoApiClient)
                        KlaviyoApiClient.startService()
                    }
                    KlaviyoApiClient
                }

            apiClient.flushQueue()
        } catch (e: Exception) {
            Registry.log.error("WorkManager queue flush failed", e)
            // Return success anyway - we don't want WorkManager to retry
            // The queue will be flushed on next opportunity
        }

        return Result.success()
    }
}

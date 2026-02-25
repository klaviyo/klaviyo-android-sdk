package com.klaviyo.analytics.networking

/**
 * Interface for scheduling network queue flush operations
 *
 * Provides an abstraction layer for different scheduling strategies.
 * This allows the SDK to use different mechanisms (WorkManager, AlarmManager, etc.)
 * for triggering queue flush operations based on system constraints.
 */
internal interface QueueScheduler {

    /**
     * Schedule a queue flush operation
     *
     * The implementation should determine when to execute the flush based on
     * system constraints (network availability, Doze mode, etc.). The flush
     * may execute immediately if conditions are met, or be deferred until
     * appropriate conditions are satisfied.
     */
    fun scheduleFlush()

    /**
     * Cancel any scheduled queue flush operations
     *
     * Cancels pending flush operations that haven't executed yet.
     * This is useful when the queue has been manually flushed or when
     * shutting down the SDK.
     */
    fun cancelScheduledFlush()
}

package com.klaviyo.inbox

/**
 * The single entry point into inbox data
 */
internal interface InboxRepository {
    /**
     * Store a captured push, deduplicating on [InboxCaptureRecord.dedupeKey]
     */
    suspend fun capture(record: InboxCaptureRecord): InboxCaptureResult
}

internal enum class InboxCaptureResult { CAPTURED, DUPLICATE, SKIPPED, FAILED }

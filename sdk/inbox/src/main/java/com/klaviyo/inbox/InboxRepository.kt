package com.klaviyo.inbox

/**
 * The single entry point into inbox data
 */
internal interface InboxRepository {
    /**
     * Store a captured push, deduplicating on [InboxMessageEntity.transmissionId]
     */
    suspend fun capture(message: InboxMessageEntity): InboxCaptureResult

    /**
     * The current company's unread, unarchived message count. Zero before the SDK is initialized.
     *
     * @throws InboxException when the store fails
     */
    suspend fun fetchUnreadCount(): Int

    /**
     * Remove every stored message captured under [companyId]
     */
    suspend fun clearByCompanyId(companyId: String)

    /**
     * Remove every stored message
     */
    suspend fun clearAll()
}

internal enum class InboxCaptureResult {
    CAPTURED,
    DUPLICATE,

    /**
     * The store could not write the message. The repository logs the cause at error.
     */
    FAILED
}

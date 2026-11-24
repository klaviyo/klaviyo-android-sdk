package com.klaviyo.inbox

import kotlinx.coroutines.flow.Flow

/**
 * The single entry point into inbox data
 */
internal interface InboxRepository {
    /**
     * Emits when stored messages change. See [LocalInboxDataSource.changes].
     */
    val changes: Flow<Unit>

    /**
     * Store a captured push, deduplicating on [InboxMessageEntity.transmissionId]
     */
    suspend fun capture(message: InboxMessageEntity): InboxCaptureResult

    /**
     * One page of the current company's messages, newest first. Empty before the SDK is
     * initialized.
     *
     * @throws InboxException when the cursor is invalid or the store fails
     */
    suspend fun fetchPage(query: InboxQuery): InboxPage

    /**
     * The current company's unread, unarchived message count. Zero before the SDK is initialized.
     *
     * @throws InboxException when the store fails
     */
    suspend fun fetchUnreadCount(): Int

    /**
     * Apply [change] to the current company's message. False before the SDK is initialized, or
     * when the message isn't stored.
     *
     * @throws InboxException when the store fails
     */
    suspend fun updateMessageState(messageId: String, change: InboxStateChange): Boolean

    /**
     * Remove every message the current company captured on this device, read and archived alike.
     * Zero before the SDK is initialized.
     *
     * @return How many messages were removed
     * @throws InboxException.StorageFailure when the store fails
     */
    suspend fun clearCurrentCompany(): Int

    /**
     * Remove every stored message captured under [companyId]. Best-effort: a store failure is
     * logged and does not throw, and no count is reported.
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

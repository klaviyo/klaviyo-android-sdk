package com.klaviyo.inbox

import kotlinx.coroutines.flow.Flow

/**
 * The single entry point into inbox data
 */
internal interface InboxRepository {
    /**
     * Store a captured push, deduplicating on [InboxMessageEntity.transmissionId]
     */
    suspend fun capture(message: InboxMessageEntity): InboxCaptureResult

    /**
     * One page of the current company's messages. Empty before the SDK is initialized.
     *
     * @throws InboxException when the cursor is invalid or the store fails
     */
    suspend fun fetchPage(query: InboxQuery): InboxPage

    /**
     * @throws InboxException when the store fails
     */
    suspend fun fetchUnreadCount(): Int

    /**
     * @return True when the message's state changed
     * @throws InboxException when the store fails
     */
    suspend fun updateMessageState(messageId: String, change: InboxStateChange): Boolean

    /**
     * Emits when stored messages change. See [MobileInbox.changes].
     */
    val changes: Flow<Unit>

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

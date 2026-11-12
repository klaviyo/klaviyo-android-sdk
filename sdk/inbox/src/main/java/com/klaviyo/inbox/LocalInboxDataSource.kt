package com.klaviyo.inbox

import kotlinx.coroutines.flow.Flow

/**
 * The on-device inbox store. Enforces deduplication and retention so every caller gets the same
 * behavior.
 */
internal interface LocalInboxDataSource {
    /**
     * Emits once after each committed write that changed what a read returns: a stored push
     * (together with the trim it triggers), a state change that altered a message, and a clear that
     * removed messages. Duplicates and changes that alter nothing do not emit.
     *
     * Conflated and never blocks a writer: emissions that pile up while a collector is busy merge
     * into one. Nothing is replayed, so collect before reading.
     *
     * Collecting never opens the store. An emission can come from any company's write.
     */
    val changes: Flow<Unit>

    /**
     * Store [message] unless a message with the same ID is already stored, then trim the
     * message's company to the newest [retentionLimit] messages, in one transaction.
     */
    suspend fun insert(message: InboxMessageEntity, retentionLimit: Int): InboxInsertResult

    /**
     * One page of [companyId]'s messages, newest first. Empty when nothing has ever been stored.
     */
    suspend fun fetchPage(companyId: String, query: InboxRecordQuery): InboxRecordPage

    /**
     * The number of [companyId]'s messages that are unread and not archived. Zero when nothing
     * has ever been stored.
     */
    suspend fun countUnread(companyId: String): Int

    /**
     * Apply [change] to [companyId]'s message, stamping [updatedAt] and marking the message as
     * pending sync when the change alters it.
     *
     * @return True when the company has the message, whether or not the change altered it. False
     *  when nothing has ever been stored.
     */
    suspend fun updateState(
        companyId: String,
        messageId: String,
        change: InboxStateChange,
        updatedAt: Long
    ): Boolean

    suspend fun clearByCompanyId(companyId: String)

    suspend fun clearAll()
}

internal enum class InboxInsertResult {
    INSERTED,
    DUPLICATE
}

/**
 * Where a page starts: just after the message with this sort time and ID
 */
internal data class InboxRecordPosition(val sortTime: Long, val transmissionId: String)

/**
 * @property after Null for the first page
 */
internal data class InboxRecordQuery(val pageSize: Int, val after: InboxRecordPosition?)

/**
 * @property hasMore True when more messages follow [messages]
 */
internal data class InboxRecordPage(
    val messages: List<InboxMessageEntity>,
    val hasMore: Boolean
)

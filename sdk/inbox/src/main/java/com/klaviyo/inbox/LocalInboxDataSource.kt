package com.klaviyo.inbox

/**
 * The on-device inbox store. Enforces deduplication and retention so every caller gets the same
 * behavior.
 */
internal interface LocalInboxDataSource {
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

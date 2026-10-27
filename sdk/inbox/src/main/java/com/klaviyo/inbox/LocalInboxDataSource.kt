package com.klaviyo.inbox

import kotlinx.coroutines.flow.Flow

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
     * One page of [companyId]'s messages. Empty when nothing has ever been stored.
     */
    suspend fun fetchPage(companyId: String, query: InboxRecordQuery): InboxRecordPage

    /**
     * The number of [companyId]'s messages that are unread and not archived
     */
    suspend fun countUnread(companyId: String): Int

    /**
     * Apply [change] to one of [companyId]'s messages
     *
     * @return True when the message's state changed, false when it was unknown or already in
     *  that state
     */
    suspend fun updateState(
        companyId: String,
        id: String,
        change: InboxRecordStateChange
    ): Boolean

    suspend fun clearByCompanyId(companyId: String)

    suspend fun clearAll()

    /**
     * Emits after every committed change to the stored messages: an insert and the retention
     * trim that came with it, a state change, or a clear that removed messages. Writes that
     * change nothing don't emit. Conflated, and holds no data.
     */
    val changes: Flow<Unit>
}

internal enum class InboxInsertResult {
    INSERTED,
    DUPLICATE
}

/**
 * Where a page starts: just after the message with this sort time and ID, in the query's order
 */
internal data class InboxRecordPosition(val sortTime: Long, val transmissionId: String)

/**
 * @property after Null for the first page
 */
internal data class InboxRecordQuery(
    val pageSize: Int,
    val after: InboxRecordPosition?,
    val readFilter: InboxReadFilter,
    val archiveFilter: InboxArchiveFilter,
    val sortOrder: InboxSortOrder
)

/**
 * @property hasMore True when more messages follow [messages] in the query's order
 */
internal data class InboxRecordPage(
    val messages: List<InboxMessageEntity>,
    val hasMore: Boolean
)

internal enum class InboxRecordStateChange {
    MARK_READ,
    MARK_UNREAD,
    ARCHIVE,
    UNARCHIVE
}

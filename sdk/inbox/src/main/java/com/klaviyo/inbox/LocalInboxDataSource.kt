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
     * One page of [companyId]'s messages that match the query's state filters, newest first. Empty
     * when nothing has ever been stored.
     */
    suspend fun fetchPage(companyId: String, query: InboxRecordQuery): InboxRecordPage

    /**
     * The number of [companyId]'s messages that are unread and not archived. Zero when nothing
     * has ever been stored.
     */
    suspend fun countUnread(companyId: String): Int

    /**
     * Apply [change] to [companyId]'s message, stamping [updatedAt] when the change alters
     * it.
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

    /**
     * Apply [change] to every one of [companyId]'s messages that matches [isRead] and
     * [isArchived], in one statement. Messages already in the target state are not touched, and
     * the rest are stamped with [updatedAt]. Emits [changes] once, only when something changed.
     *
     * @param isRead Only messages with this read state, or null for either
     * @param isArchived Only messages with this archived state, or null for either
     * @return How many messages changed. Zero when nothing has ever been stored.
     */
    suspend fun updateStateWhere(
        companyId: String,
        change: InboxStateChange,
        isRead: Boolean?,
        isArchived: Boolean?,
        updatedAt: Long
    ): Int

    /**
     * Apply [change] to the [messageIds] that belong to [companyId]. Unknown IDs and other
     * companies' messages are ignored, a duplicate ID counts once, and messages already in the
     * target state are not touched. The rest are stamped with [updatedAt]. Large lists are split
     * into several statements within one transaction. Emits [changes] once, only when something
     * changed.
     *
     * @return How many messages changed. Zero for an empty list or when nothing has ever been
     *  stored.
     */
    suspend fun updateStateForIds(
        companyId: String,
        change: InboxStateChange,
        messageIds: List<String>,
        updatedAt: Long
    ): Int

    /**
     * Remove every one of [companyId]'s messages and emit once when it removed any.
     *
     * @return How many messages were removed
     */
    suspend fun clearByCompanyId(companyId: String): Int

    suspend fun clearAll()
}

internal enum class InboxInsertResult {
    INSERTED,
    DUPLICATE
}

/**
 * Where a page starts: just after the message with this send time and ID
 */
internal data class InboxRecordPosition(val sentAt: Long, val transmissionId: String)

/**
 * @property after Null for the first page
 * @property isRead Only messages with this read state, or null for either
 * @property isArchived Only messages with this archived state, or null for either
 */
internal data class InboxRecordQuery(
    val pageSize: Int,
    val after: InboxRecordPosition?,
    val isRead: Boolean? = null,
    val isArchived: Boolean? = null
)

/**
 * @property hasMore True when more messages follow [messages]
 */
internal data class InboxRecordPage(
    val messages: List<InboxMessageEntity>,
    val hasMore: Boolean
)

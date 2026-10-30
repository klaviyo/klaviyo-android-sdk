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

    suspend fun clearByCompanyId(companyId: String)

    suspend fun clearAll()
}

internal enum class InboxInsertResult {
    INSERTED,
    DUPLICATE
}

package com.klaviyo.inbox

import com.klaviyo.core.KlaviyoException
import com.klaviyo.core.Registry
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * The SDK's [InboxRepository]. A capture failure is logged and reported as
 * [InboxCaptureResult.FAILED] so a store problem never reaches the push path. Host-facing reads
 * and writes report a store problem as [InboxException].
 */
internal class KlaviyoInboxRepository(
    private val dataSource: LocalInboxDataSource,
    private val retentionLimit: Int = DEFAULT_RETENTION_LIMIT
) : InboxRepository {

    override val changes: Flow<Unit> get() = dataSource.changes

    override suspend fun capture(message: InboxMessageEntity): InboxCaptureResult = try {
        when (dataSource.insert(message, retentionLimit)) {
            InboxInsertResult.INSERTED -> InboxCaptureResult.CAPTURED
            InboxInsertResult.DUPLICATE -> InboxCaptureResult.DUPLICATE
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Registry.log.error("Mobile Inbox failed to store a push", e)
        InboxCaptureResult.FAILED
    }

    override suspend fun fetchPage(query: InboxQuery): InboxPage {
        val after = InboxCursorCodec.decode(query)
        val companyId = currentCompanyId() ?: return InboxPage(emptyList(), nextCursor = null)

        val page = storeCall("fetch a page") {
            dataSource.fetchPage(
                companyId,
                InboxRecordQuery(
                    pageSize = query.pageSize,
                    after = after,
                    readFilter = query.readFilter,
                    archiveFilter = query.archiveFilter,
                    sortOrder = query.sortOrder
                )
            )
        }

        return InboxPage(
            messages = page.messages.map { it.toInboxMessage() },
            nextCursor = page.messages.lastOrNull()
                ?.takeIf { page.hasMore }
                ?.let { InboxCursorCodec.encode(query, it) }
        )
    }

    override suspend fun fetchUnreadCount(): Int {
        val companyId = currentCompanyId() ?: return 0
        return storeCall("count unread messages") { dataSource.countUnread(companyId) }
    }

    override suspend fun updateMessageState(messageId: String, change: InboxStateChange): Boolean {
        val companyId = currentCompanyId() ?: return false
        return storeCall("update a message") {
            dataSource.updateState(companyId, messageId, change.toRecordChange())
        }
    }

    override suspend fun clearByCompanyId(companyId: String) = dataSource.clearByCompanyId(
        companyId
    )

    override suspend fun clearAll() = dataSource.clearAll()

    /**
     * The inbox belongs to the current API key, which doesn't exist before the SDK is initialized
     */
    private fun currentCompanyId(): String? = try {
        Registry.config.apiKey
    } catch (e: KlaviyoException) {
        Registry.log.debug("Mobile Inbox read before the SDK was initialized: ${e.message}")
        null
    }

    private suspend fun <T> storeCall(action: String, call: suspend () -> T): T = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Registry.log.error("Mobile Inbox failed to $action", e)
        throw InboxException(InboxException.Reason.STORAGE_FAILURE, "Failed to $action", e)
    }

    private fun InboxStateChange.toRecordChange() = when (this) {
        InboxStateChange.MARK_READ -> InboxRecordStateChange.MARK_READ
        InboxStateChange.MARK_UNREAD -> InboxRecordStateChange.MARK_UNREAD
        InboxStateChange.ARCHIVE -> InboxRecordStateChange.ARCHIVE
        InboxStateChange.UNARCHIVE -> InboxRecordStateChange.UNARCHIVE
    }

    companion object {
        const val DEFAULT_RETENTION_LIMIT = 100
    }
}

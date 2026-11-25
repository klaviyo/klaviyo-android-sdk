package com.klaviyo.inbox

import com.klaviyo.core.KlaviyoException
import com.klaviyo.core.Registry
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * The SDK's [InboxRepository]. Storage failures are logged with their cause. A capture reports
 * [InboxCaptureResult.FAILED], and host-facing calls report a store problem as [InboxException].
 *
 * Every call runs through the gate [resetGate] returns, so a pending profile reset is cleared
 * before the call reads or writes the store.
 */
internal class KlaviyoInboxRepository(
    private val dataSource: LocalInboxDataSource,
    private val resetGate: () -> InboxResetGate,
    private val retentionLimit: Int = DEFAULT_RETENTION_LIMIT
) : InboxRepository {

    constructor(
        dataSource: LocalInboxDataSource,
        resetGate: InboxResetGate,
        retentionLimit: Int = DEFAULT_RETENTION_LIMIT
    ) : this(dataSource, { resetGate }, retentionLimit)

    override val changes: Flow<Unit> get() = dataSource.changes

    override suspend fun capture(message: InboxMessageEntity): InboxCaptureResult = guarded(
        "store a push",
        InboxCaptureResult.FAILED
    ) {
        resetGate().withCurrentInbox {
            when (dataSource.insert(message, retentionLimit)) {
                InboxInsertResult.INSERTED -> InboxCaptureResult.CAPTURED
                InboxInsertResult.DUPLICATE -> InboxCaptureResult.DUPLICATE
            }
        }
    }

    override suspend fun clearByCompanyId(companyId: String) {
        guarded("clear messages for a company", Unit) {
            resetGate().withCurrentInbox { dataSource.clearByCompanyId(companyId) }
        }
    }

    override suspend fun clearCurrentCompany(): Int {
        val companyId = currentCompanyId() ?: return 0
        return storeCall("clear the current company's messages") {
            resetGate().withCurrentInbox { dataSource.clearByCompanyId(companyId) }
        }
    }

    override suspend fun clearAll() = guarded("clear all messages", Unit) {
        resetGate().withCurrentInbox { dataSource.clearAll() }
    }

    /**
     * Runs a storage call, logging a failure with its cause and returning [onFailure] so a store
     * problem never reaches the caller. Cancellation passes through.
     */
    private suspend fun <T> guarded(action: String, onFailure: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Registry.log.error("Mobile Inbox failed to $action", e)
        onFailure
    }

    override suspend fun fetchPage(query: InboxQuery): InboxPage {
        val companyId = currentCompanyId() ?: return InboxPage(emptyList(), nextCursor = null)
        val after = InboxCursorCodec.decode(query, companyId)

        val page = storeCall("fetch a page") {
            resetGate().withCurrentInbox {
                dataSource.fetchPage(
                    companyId,
                    InboxRecordQuery(
                        query.pageSize,
                        after,
                        isRead = query.readFilter.toState(),
                        isArchived = query.archiveFilter.toState()
                    )
                )
            }
        }

        return InboxPage(
            messages = page.messages.map { it.toInboxMessage() },
            nextCursor = page.messages.lastOrNull()
                ?.takeIf { page.hasMore }
                ?.let { InboxCursorCodec.encode(it, query) }
        )
    }

    override suspend fun fetchUnreadCount(): Int {
        val companyId = currentCompanyId() ?: return 0
        return storeCall("count unread messages") {
            resetGate().withCurrentInbox { dataSource.countUnread(companyId) }
        }
    }

    override suspend fun updateMessageState(
        messageId: String,
        change: InboxStateChange
    ): Boolean {
        val companyId = currentCompanyId() ?: return false
        return storeCall("update a message's state") {
            resetGate().withCurrentInbox {
                dataSource.updateState(
                    companyId,
                    messageId,
                    change,
                    Registry.clock.currentTimeMillis()
                )
            }
        }
    }

    override suspend fun updateMessageState(change: InboxStateChange, filter: InboxFilter): Int {
        val companyId = currentCompanyId() ?: return 0
        return storeCall("update the state of matching messages") {
            resetGate().withCurrentInbox {
                dataSource.updateStateWhere(
                    companyId,
                    change,
                    isRead = filter.readFilter.toState(),
                    isArchived = filter.archiveFilter.toState(),
                    updatedAt = Registry.clock.currentTimeMillis()
                )
            }
        }
    }

    /**
     * Runs a store call, logging a failure with its cause and reporting it as [InboxException].
     * Cancellation passes through.
     */
    private suspend fun <T> storeCall(action: String, call: suspend () -> T): T = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Registry.log.error("Mobile Inbox failed to $action", e)
        throw InboxException.StorageFailure("Failed to $action", e)
    }

    /**
     * The inbox belongs to the current API key, which doesn't exist before the SDK is initialized
     */
    private fun currentCompanyId(): String? = try {
        Registry.config.apiKey
    } catch (e: KlaviyoException) {
        Registry.log.debug("Mobile Inbox read before the SDK was initialized: ${e.message}")
        null
    }

    private fun InboxReadFilter.toState(): Boolean? = when (this) {
        InboxReadFilter.ALL -> null
        InboxReadFilter.UNREAD -> false
        InboxReadFilter.READ -> true
    }

    private fun InboxArchiveFilter.toState(): Boolean? = when (this) {
        InboxArchiveFilter.ACTIVE -> false
        InboxArchiveFilter.ARCHIVED -> true
        InboxArchiveFilter.ALL -> null
    }

    companion object {
        const val DEFAULT_RETENTION_LIMIT = 100
    }
}

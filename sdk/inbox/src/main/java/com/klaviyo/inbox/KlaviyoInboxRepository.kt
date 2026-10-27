package com.klaviyo.inbox

import com.klaviyo.core.KlaviyoException
import com.klaviyo.core.Registry
import kotlin.coroutines.cancellation.CancellationException

/**
 * The SDK's [InboxRepository]. Storage failures are logged with their cause. A capture reports
 * [InboxCaptureResult.FAILED] and a clear returns normally, so a store problem never reaches the
 * push path. Host-facing reads report a store problem as [InboxException].
 */
internal class KlaviyoInboxRepository(
    private val dataSource: LocalInboxDataSource,
    private val retentionLimit: Int = DEFAULT_RETENTION_LIMIT
) : InboxRepository {

    override suspend fun capture(message: InboxMessageEntity): InboxCaptureResult = guarded(
        "store a push",
        InboxCaptureResult.FAILED
    ) {
        when (dataSource.insert(message, retentionLimit)) {
            InboxInsertResult.INSERTED -> InboxCaptureResult.CAPTURED
            InboxInsertResult.DUPLICATE -> InboxCaptureResult.DUPLICATE
        }
    }

    override suspend fun clearByCompanyId(companyId: String) = guarded(
        "clear messages for a company",
        Unit
    ) {
        dataSource.clearByCompanyId(companyId)
    }

    override suspend fun clearAll() = guarded("clear all messages", Unit) {
        dataSource.clearAll()
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

    override suspend fun fetchUnreadCount(): Int {
        val companyId = currentCompanyId() ?: return 0
        return try {
            dataSource.countUnread(companyId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Registry.log.error("Mobile Inbox failed to count unread messages", e)
            throw InboxException(
                InboxException.Reason.STORAGE_FAILURE,
                "Failed to count unread messages",
                e
            )
        }
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

    companion object {
        const val DEFAULT_RETENTION_LIMIT = 100
    }
}

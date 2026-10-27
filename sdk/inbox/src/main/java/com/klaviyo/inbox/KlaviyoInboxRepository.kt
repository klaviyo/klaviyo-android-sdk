package com.klaviyo.inbox

import com.klaviyo.core.Registry
import kotlin.coroutines.cancellation.CancellationException

/**
 * The SDK's [InboxRepository]. Storage failures are logged and reported as [InboxCaptureResult.FAILED]
 * so a store problem never reaches the caller.
 */
internal class KlaviyoInboxRepository(
    private val dataSource: LocalInboxDataSource,
    private val retentionLimit: Int = DEFAULT_RETENTION_LIMIT
) : InboxRepository {

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

    override suspend fun clearByCompanyId(companyId: String) = dataSource.clearByCompanyId(
        companyId
    )

    override suspend fun clearAll() = dataSource.clearAll()

    companion object {
        const val DEFAULT_RETENTION_LIMIT = 100
    }
}

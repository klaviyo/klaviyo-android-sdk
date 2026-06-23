package com.klaviyo.mobileInbox

import com.klaviyo.core.Registry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class InboxRepositoryImpl(
    private val dao: InboxMessageDao,
    private val apiService: InboxApiService,
    private val profileParams: () -> InboxProfileParams
) : InboxRepository {

    override fun observeMessages(): Flow<List<InboxMessage>> =
        dao.observeMessages().map { entities -> entities.map { it.toInboxMessage() } }

    override fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    override suspend fun sync() {
        val params = profileParams()
        if (!params.hasIdentifier) {
            Registry.log.warning("Skipping inbox sync: no profile identifier available")
            return
        }

        val fetched = apiService.fetchMessages(params)
        if (fetched.isEmpty()) {
            Registry.log.verbose("Inbox sync returned no messages")
            return
        }

        val fetchedIds = fetched.map { it.id }
        val existingByIds = dao.getMessagesByIds(fetchedIds).associateBy { it.id }

        val toUpsert = fetched.map { message ->
            val existing = existingByIds[message.id]
            val preservedStatus = existing?.status?.takeIf { it != InboxStatus.UNREAD }
            InboxMessageEntity(
                id = message.id,
                timestamp = message.timestamp,
                title = message.title,
                body = message.body,
                status = preservedStatus ?: InboxStatus.UNREAD,
                source = message.source
            )
        }

        dao.upsertMessages(toUpsert)
        Registry.log.verbose("Inbox sync upserted ${toUpsert.size} messages")
    }

    override suspend fun markRead(id: String) {
        dao.updateStatus(id, InboxStatus.READ)
    }

    override suspend fun markHidden(id: String) {
        dao.updateStatus(id, InboxStatus.HIDDEN)
    }

    override suspend fun upsertFromPush(entity: InboxMessageEntity) {
        dao.upsertMessages(listOf(entity))
    }
}

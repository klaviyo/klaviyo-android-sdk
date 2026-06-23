package com.klaviyo.mobileInbox

import kotlinx.coroutines.flow.Flow

internal interface InboxRepository {
    fun observeMessages(): Flow<List<InboxMessage>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun sync()
    suspend fun markRead(id: String)
    suspend fun markHidden(id: String)
    suspend fun upsertFromPush(entity: InboxMessageEntity)
}

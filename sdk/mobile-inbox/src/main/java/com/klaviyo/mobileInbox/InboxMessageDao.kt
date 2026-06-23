package com.klaviyo.mobileInbox

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface InboxMessageDao {
    @Upsert
    suspend fun upsertMessages(messages: List<InboxMessageEntity>)

    @Query("SELECT * FROM inbox_messages WHERE status != 'HIDDEN' ORDER BY timestamp DESC")
    fun observeMessages(): Flow<List<InboxMessageEntity>>

    @Query("SELECT * FROM inbox_messages WHERE status != 'HIDDEN' ORDER BY timestamp DESC")
    suspend fun getMessages(): List<InboxMessageEntity>

    @Query("SELECT * FROM inbox_messages WHERE id IN (:ids)")
    suspend fun getMessagesByIds(ids: List<String>): List<InboxMessageEntity>

    @Query("UPDATE inbox_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: InboxStatus)

    @Query("SELECT COUNT(*) FROM inbox_messages WHERE status = 'UNREAD'")
    fun observeUnreadCount(): Flow<Int>
}

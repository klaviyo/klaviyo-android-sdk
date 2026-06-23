package com.klaviyo.mobileInbox

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "inbox_messages")
@TypeConverters(InboxConverters::class)
internal data class InboxMessageEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val title: String,
    val body: String,
    val status: InboxStatus = InboxStatus.UNREAD,
    val source: InboxSource
) {
    fun toInboxMessage() = InboxMessage(
        id = id,
        timestamp = timestamp,
        title = title,
        body = body,
        status = status,
        source = source
    )
}

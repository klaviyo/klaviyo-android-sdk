package com.klaviyo.mobileInbox

import androidx.room.TypeConverter

internal class InboxConverters {
    @TypeConverter
    fun fromInboxStatus(value: InboxStatus): String = value.name

    @TypeConverter
    fun toInboxStatus(value: String): InboxStatus = InboxStatus.valueOf(value)

    @TypeConverter
    fun fromInboxSource(value: InboxSource): String = value.name

    @TypeConverter
    fun toInboxSource(value: String): InboxSource = InboxSource.valueOf(value)
}

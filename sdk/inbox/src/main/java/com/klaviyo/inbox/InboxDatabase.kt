package com.klaviyo.inbox

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [InboxMessageEntity::class],
    version = InboxDatabase.VERSION,
    exportSchema = true
)
@TypeConverters(InboxConverters::class)
internal abstract class InboxDatabase : RoomDatabase() {
    abstract fun messageDao(): InboxMessageDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "klaviyo_inbox.db"
    }
}

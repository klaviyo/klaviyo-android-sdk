package com.klaviyo.mobileInbox

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [InboxMessageEntity::class], version = 2, exportSchema = false)
internal abstract class InboxDatabase : RoomDatabase() {
    abstract fun inboxMessageDao(): InboxMessageDao

    companion object {
        private const val DATABASE_NAME = "klaviyo_inbox.db"

        @Volatile
        private var instance: InboxDatabase? = null

        fun getInstance(context: Context): InboxDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    InboxDatabase::class.java,
                    DATABASE_NAME
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}

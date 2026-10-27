package com.klaviyo.inbox

import android.content.Context
import androidx.room.Room

/**
 * [LocalInboxDataSource] backed by an app-private Room database, opened on first use
 *
 * @param context Used to locate the database file. The application context is retained.
 */
internal class RoomInboxDataSource(context: Context) : LocalInboxDataSource {

    private val appContext = context.applicationContext

    private val dao: InboxMessageDao by lazy {
        Room.databaseBuilder(appContext, InboxDatabase::class.java, InboxDatabase.FILE_NAME)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
            .messageDao()
    }

    override suspend fun insert(message: InboxMessageEntity, retentionLimit: Int): InboxInsertResult =
        if (dao.insertAndTrim(message, retentionLimit)) {
            InboxInsertResult.INSERTED
        } else {
            InboxInsertResult.DUPLICATE
        }

    override suspend fun clearByCompanyId(companyId: String) {
        dao.deleteByCompanyId(companyId)
    }

    override suspend fun clearAll() {
        dao.deleteAll()
    }
}

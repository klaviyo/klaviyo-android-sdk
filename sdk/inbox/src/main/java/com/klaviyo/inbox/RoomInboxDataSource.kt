package com.klaviyo.inbox

import android.content.Context
import androidx.room.Room

/**
 * [LocalInboxDataSource] backed by an app-private Room database, opened on first use
 *
 * Reads never create the database: until a push has been captured they behave as an empty store.
 *
 * @param contextProvider Supplies the context used to locate the database file. Called on first
 *  use, so a context that doesn't exist yet doesn't fail construction.
 * @param databaseName The database file name inside the app's private database directory
 */
internal class RoomInboxDataSource(
    contextProvider: () -> Context,
    private val databaseName: String = InboxDatabase.FILE_NAME
) : LocalInboxDataSource {

    constructor(
        context: Context,
        databaseName: String = InboxDatabase.FILE_NAME
    ) : this({ context }, databaseName)

    private val appContext by lazy { contextProvider().applicationContext }

    private val databaseDelegate = lazy {
        Room.databaseBuilder(appContext, InboxDatabase::class.java, databaseName)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    private val dao: InboxMessageDao get() = databaseDelegate.value.messageDao()

    /**
     * Whether a read could find anything, without opening the database to find out
     */
    private val storeExists: Boolean
        get() = databaseDelegate.isInitialized() || appContext.getDatabasePath(databaseName).exists()

    override suspend fun insert(message: InboxMessageEntity, retentionLimit: Int): InboxInsertResult =
        if (dao.insertAndTrim(message, retentionLimit)) {
            InboxInsertResult.INSERTED
        } else {
            InboxInsertResult.DUPLICATE
        }

    override suspend fun fetchPage(companyId: String, query: InboxRecordQuery): InboxRecordPage {
        if (!storeExists) return InboxRecordPage(emptyList(), hasMore = false)

        // One extra row tells whether another page follows
        val rows = dao.pageNewestFirst(
            companyId,
            query.after?.sortTime,
            query.after?.transmissionId,
            limit = query.pageSize + 1
        )
        return InboxRecordPage(rows.take(query.pageSize), hasMore = rows.size > query.pageSize)
    }

    override suspend fun countUnread(companyId: String): Int =
        if (storeExists) dao.countUnread(companyId) else 0

    override suspend fun clearByCompanyId(companyId: String) {
        dao.deleteByCompanyId(companyId)
    }

    override suspend fun clearAll() {
        dao.deleteAll()
    }

    /**
     * Closes the database if it was opened. The data source is not usable afterwards.
     */
    fun close() {
        if (databaseDelegate.isInitialized()) databaseDelegate.value.close()
    }
}

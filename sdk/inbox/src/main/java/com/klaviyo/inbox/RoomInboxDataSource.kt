package com.klaviyo.inbox

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * [LocalInboxDataSource] backed by an app-private Room database, opened on first use
 *
 * Reads and state changes never create the database: until a push has been captured they behave
 * as an empty store.
 *
 * @param context Used to locate the database file. The application context is retained.
 * @param databaseName The database file name inside the app's private database directory
 */
internal class RoomInboxDataSource(
    context: Context,
    private val databaseName: String = InboxDatabase.FILE_NAME
) : LocalInboxDataSource {

    private val appContext = context.applicationContext

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

    private val changeSignal = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val changes: Flow<Unit> = changeSignal.asSharedFlow()

    override suspend fun insert(message: InboxMessageEntity, retentionLimit: Int): InboxInsertResult =
        if (dao.insertAndTrim(message, retentionLimit)) {
            changeSignal.tryEmit(Unit)
            InboxInsertResult.INSERTED
        } else {
            InboxInsertResult.DUPLICATE
        }

    override suspend fun fetchPage(companyId: String, query: InboxRecordQuery): InboxRecordPage {
        if (!storeExists) return InboxRecordPage(emptyList(), hasMore = false)

        // One extra row tells whether another page follows
        val rows = fetchRows(companyId, query, limit = query.pageSize + 1)
        return InboxRecordPage(rows.take(query.pageSize), hasMore = rows.size > query.pageSize)
    }

    private suspend fun fetchRows(
        companyId: String,
        query: InboxRecordQuery,
        limit: Int
    ): List<InboxMessageEntity> {
        val isRead = when (query.readFilter) {
            InboxReadFilter.ALL -> null
            InboxReadFilter.READ -> true
            InboxReadFilter.UNREAD -> false
        }
        val isArchived = when (query.archiveFilter) {
            InboxArchiveFilter.ALL -> null
            InboxArchiveFilter.ARCHIVED -> true
            InboxArchiveFilter.UNARCHIVED -> false
        }
        val afterSortTime = query.after?.sortTime
        val afterId = query.after?.transmissionId

        return when (query.sortOrder) {
            InboxSortOrder.NEWEST_FIRST ->
                dao.pageNewestFirst(companyId, isRead, isArchived, afterSortTime, afterId, limit)
            InboxSortOrder.OLDEST_FIRST ->
                dao.pageOldestFirst(companyId, isRead, isArchived, afterSortTime, afterId, limit)
        }
    }

    override suspend fun countUnread(companyId: String): Int =
        if (storeExists) dao.countUnread(companyId) else 0

    override suspend fun updateState(
        companyId: String,
        id: String,
        change: InboxRecordStateChange
    ): Boolean {
        if (!storeExists) return false

        val changed = when (change) {
            InboxRecordStateChange.MARK_READ -> dao.setRead(companyId, id, true)
            InboxRecordStateChange.MARK_UNREAD -> dao.setRead(companyId, id, false)
            InboxRecordStateChange.ARCHIVE -> dao.setArchived(companyId, id, true)
            InboxRecordStateChange.UNARCHIVE -> dao.setArchived(companyId, id, false)
        } > 0

        if (changed) changeSignal.tryEmit(Unit)
        return changed
    }

    override suspend fun clearByCompanyId(companyId: String) {
        if (storeExists && dao.deleteByCompanyId(companyId) > 0) changeSignal.tryEmit(Unit)
    }

    override suspend fun clearAll() {
        if (storeExists && dao.deleteAll() > 0) changeSignal.tryEmit(Unit)
    }

    /**
     * Closes the database if it was opened. The data source is not usable afterwards.
     */
    fun close() {
        if (databaseDelegate.isInitialized()) databaseDelegate.value.close()
    }
}

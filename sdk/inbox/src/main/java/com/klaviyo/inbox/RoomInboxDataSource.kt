package com.klaviyo.inbox

import android.content.Context
import androidx.room.Room
import com.klaviyo.core.Registry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

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

    private val changeSignal = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val changes: Flow<Unit> = changeSignal.asSharedFlow()

    private val appContext by lazy { contextProvider().applicationContext }

    private val databaseDelegate = lazy {
        Room.databaseBuilder(appContext, InboxDatabase::class.java, databaseName)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    /**
     * The first call builds the database, which loads the generated Room classes by reflection, so
     * it runs on the background dispatcher. Later calls skip the hop.
     */
    private suspend fun dao(): InboxMessageDao = if (databaseDelegate.isInitialized()) {
        databaseDelegate.value.messageDao()
    } else {
        withContext(Registry.dispatcher) { databaseDelegate.value.messageDao() }
    }

    /**
     * Whether a read could find anything, without opening the database to find out. Checks the
     * file system, so it runs on the background dispatcher.
     */
    private suspend fun storeExists(): Boolean = databaseDelegate.isInitialized() ||
        withContext(Registry.dispatcher) { appContext.getDatabasePath(databaseName).exists() }

    override suspend fun insert(message: InboxMessageEntity, retentionLimit: Int): InboxInsertResult =
        if (dao().insertAndTrim(message, retentionLimit)) {
            changeSignal.tryEmit(Unit)
            InboxInsertResult.INSERTED
        } else {
            InboxInsertResult.DUPLICATE
        }

    override suspend fun fetchPage(companyId: String, query: InboxRecordQuery): InboxRecordPage {
        if (!storeExists()) return InboxRecordPage(emptyList(), hasMore = false)

        // One extra row tells whether another page follows
        val rows = dao().pageNewestFirst(
            companyId,
            query.after?.sentAt,
            query.after?.transmissionId,
            query.isRead,
            query.isArchived,
            limit = query.pageSize + 1
        )
        return InboxRecordPage(rows.take(query.pageSize), hasMore = rows.size > query.pageSize)
    }

    override suspend fun fetchMessage(companyId: String, messageId: String): InboxMessageEntity? =
        if (storeExists()) dao().get(messageId)?.takeIf { it.companyId == companyId } else null

    override suspend fun countUnread(companyId: String): Int =
        if (storeExists()) dao().countUnread(companyId) else 0

    override suspend fun updateState(
        companyId: String,
        messageId: String,
        change: InboxStateChange,
        updatedAt: Long
    ): Boolean {
        if (!storeExists()) return false
        return when (dao().updateState(companyId, messageId, change, updatedAt)) {
            InboxStateUpdate.ALTERED -> true.also { changeSignal.tryEmit(Unit) }
            InboxStateUpdate.UNCHANGED -> true
            InboxStateUpdate.NOT_FOUND -> false
        }
    }

    override suspend fun updateStateWhere(
        companyId: String,
        change: InboxStateChange,
        isRead: Boolean?,
        isArchived: Boolean?,
        updatedAt: Long
    ): Int {
        if (!storeExists()) return 0
        return dao().updateStateWhere(companyId, change, isRead, isArchived, updatedAt)
            .also { if (it > 0) changeSignal.tryEmit(Unit) }
    }

    override suspend fun clearByCompanyId(companyId: String): Int {
        val removed = dao().deleteByCompanyId(companyId)
        if (removed > 0) changeSignal.tryEmit(Unit)
        return removed
    }

    override suspend fun clearAll() {
        if (dao().deleteAll() > 0) changeSignal.tryEmit(Unit)
    }

    /**
     * Closes the database if it was opened. The data source is not usable afterwards.
     */
    fun close() {
        if (databaseDelegate.isInitialized()) databaseDelegate.value.close()
    }
}

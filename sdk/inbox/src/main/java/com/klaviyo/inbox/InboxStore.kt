package com.klaviyo.inbox

import com.klaviyo.core.Registry
import com.klaviyo.core.safeLaunch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * Location and lifecycle of the on-device inbox store. The inbox database must be created
 * with [DATABASE_NAME], and any code that opens it must first call [awaitPendingDelete],
 * so a store opened after unregistering never sees, or is removed by, the previous one.
 */
internal object InboxStore {
    internal const val DATABASE_NAME = "klaviyo_inbox.db"

    private var pendingDelete: Job? = null

    /**
     * Queue deletion of the inbox database file and its journal files, without opening it.
     * Deletions run in the order they were requested.
     */
    fun deleteAsync() = synchronized(this) {
        val previous = pendingDelete
        pendingDelete = CoroutineScope(Registry.dispatcher).safeLaunch {
            previous?.join()
            delete()
        }
    }

    /**
     * Suspend until every deletion requested so far has finished.
     */
    suspend fun awaitPendingDelete() {
        synchronized(this) { pendingDelete }?.join()
    }

    private fun delete() {
        val deleted = Registry.config.applicationContext.deleteDatabase(DATABASE_NAME)
        Registry.log.verbose(
            if (deleted) "Deleted Mobile Inbox store" else "No Mobile Inbox store to delete"
        )
    }
}

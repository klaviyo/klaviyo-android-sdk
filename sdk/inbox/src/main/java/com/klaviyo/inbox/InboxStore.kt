package com.klaviyo.inbox

import com.klaviyo.core.Registry

/**
 * Location of the on-device inbox store. The inbox database must be created with this name
 * so that unregistering deletes it.
 */
internal object InboxStore {
    internal const val DATABASE_NAME = "klaviyo_inbox.db"

    /**
     * Delete the inbox database file and its journal files, without opening it.
     * Has no effect when the store has never been created.
     */
    fun delete() {
        val deleted = Registry.config.applicationContext.deleteDatabase(DATABASE_NAME)
        Registry.log.verbose(
            if (deleted) "Deleted Mobile Inbox store" else "No Mobile Inbox store to delete"
        )
    }
}

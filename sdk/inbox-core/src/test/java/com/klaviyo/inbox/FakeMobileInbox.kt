package com.klaviyo.inbox

import com.klaviyo.core.Registry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * A [MobileInbox] that answers from fields, registered the way the `inbox` module registers the
 * real one
 */
class FakeMobileInbox : MobileInbox {
    var unreadCount = 0
    var clearedCount = 0
    var clearAllCalls = 0
    var page = InboxPage(emptyList(), nextCursor = null)
    var failure: Exception? = null
    var messageExists = true
    val queries = mutableListOf<InboxQuery>()
    val stateChanges = mutableListOf<Pair<String, InboxStateChange>>()
    var bulkChangedCount = 0
    val bulkStateChanges = mutableListOf<Pair<InboxStateChange, InboxFilter>>()
    private val changeSignal = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** When set, collecting [changes] throws it, as a missing repository would */
    var changesFailure: Exception? = null

    override val changes: Flow<Unit> = flow {
        changesFailure?.let { throw it }
        emitAll(changeSignal)
    }

    /** Emits on [changes], as the real inbox does when its messages change */
    fun emitChange() = changeSignal.tryEmit(Unit)

    override suspend fun fetchPage(query: InboxQuery): InboxPage {
        queries += query
        return failure?.let { throw it } ?: page
    }

    override suspend fun fetchUnreadCount(): Int = failure?.let { throw it } ?: unreadCount

    override suspend fun updateMessageState(messageId: String, change: InboxStateChange): Boolean {
        stateChanges += messageId to change
        return failure?.let { throw it } ?: messageExists
    }

    override suspend fun updateMessageState(change: InboxStateChange, filter: InboxFilter): Int {
        bulkStateChanges += change to filter
        return failure?.let { throw it } ?: bulkChangedCount
    }

    override suspend fun clearAll(): Int {
        clearAllCalls++
        return failure?.let { throw it } ?: clearedCount
    }

    fun register() = Registry.register<MobileInboxProvider>(
        object : MobileInboxProvider {
            override val mobileInbox = this@FakeMobileInbox
        }
    )

    fun unregister() = Registry.unregister<MobileInboxProvider>()
}

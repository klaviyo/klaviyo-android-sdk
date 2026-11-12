package com.klaviyo.inbox

import com.klaviyo.core.Registry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A [MobileInbox] that answers from fields, registered the way the `inbox` module registers the
 * real one
 */
class FakeMobileInbox : MobileInbox {
    var unreadCount = 0
    var page = InboxPage(emptyList(), nextCursor = null)
    var failure: Exception? = null
    var messageExists = true
    val queries = mutableListOf<InboxQuery>()
    val stateChanges = mutableListOf<Pair<String, InboxStateChange>>()
    private val changeSignal = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val changes: Flow<Unit> = changeSignal

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

    fun register() = Registry.register<MobileInboxProvider>(
        object : MobileInboxProvider {
            override val mobileInbox = this@FakeMobileInbox
        }
    )

    fun unregister() = Registry.unregister<MobileInboxProvider>()
}

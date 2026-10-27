package com.klaviyo.inbox

import com.klaviyo.core.Registry
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A [MobileInbox] that records calls and answers from fields, registered the way the `inbox`
 * module registers the real one
 */
class FakeMobileInbox : MobileInbox {
    var page = InboxPage(emptyList(), null)
    var unreadCount = 0
    var failure: Exception? = null
    val stateChanges = mutableListOf<Pair<String, InboxStateChange>>()
    val changeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override suspend fun fetchPage(query: InboxQuery): InboxPage = failure?.let { throw it } ?: page

    override suspend fun fetchUnreadCount(): Int = failure?.let { throw it } ?: unreadCount

    override suspend fun updateMessageState(messageId: String, change: InboxStateChange) {
        failure?.let { throw it }
        stateChanges += messageId to change
    }

    override val changes = changeSignal

    fun register() = Registry.register<MobileInboxProvider>(
        object : MobileInboxProvider {
            override val mobileInbox = this@FakeMobileInbox
        }
    )

    fun unregister() = Registry.unregister<MobileInboxProvider>()
}

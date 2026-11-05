package com.klaviyo.inbox

import com.klaviyo.core.Registry

/**
 * A [MobileInbox] that answers from fields, registered the way the `inbox` module registers the
 * real one
 */
class FakeMobileInbox : MobileInbox {
    var unreadCount = 0
    var page = InboxPage(emptyList(), nextCursor = null)
    var failure: Exception? = null
    val queries = mutableListOf<InboxQuery>()

    override suspend fun fetchPage(query: InboxQuery): InboxPage {
        queries += query
        return failure?.let { throw it } ?: page
    }

    override suspend fun fetchUnreadCount(): Int = failure?.let { throw it } ?: unreadCount

    fun register() = Registry.register<MobileInboxProvider>(
        object : MobileInboxProvider {
            override val mobileInbox = this@FakeMobileInbox
        }
    )

    fun unregister() = Registry.unregister<MobileInboxProvider>()
}

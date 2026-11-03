package com.klaviyo.inbox

import com.klaviyo.core.Registry

/**
 * A [MobileInbox] that answers from fields, registered the way the `inbox` module registers the
 * real one
 */
class FakeMobileInbox : MobileInbox {
    var unreadCount = 0
    var failure: Exception? = null

    override suspend fun fetchUnreadCount(): Int = failure?.let { throw it } ?: unreadCount

    fun register() = Registry.register<MobileInboxProvider>(
        object : MobileInboxProvider {
            override val mobileInbox = this@FakeMobileInbox
        }
    )

    fun unregister() = Registry.unregister<MobileInboxProvider>()
}

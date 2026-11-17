package com.klaviyo.inbox

import com.klaviyo.core.Registry

/**
 * The SDK's [MobileInboxProvider]. Resolves the repository on each call, so it opens no store
 * until the host reads, and picks up a repository registered later.
 */
internal class KlaviyoMobileInboxProvider : MobileInboxProvider {

    override val mobileInbox: MobileInbox = object : MobileInbox {
        override suspend fun fetchPage(query: InboxQuery): InboxPage = repository.fetchPage(query)

        override suspend fun fetchUnreadCount(): Int = repository.fetchUnreadCount()

        override suspend fun updateMessageState(
            messageId: String,
            change: InboxStateChange
        ): Boolean = repository.updateMessageState(messageId, change)
    }

    private val repository: InboxRepository get() = Registry.get()
}

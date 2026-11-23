package com.klaviyo.inbox

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The SDK's [MobileInboxProvider]. Resolves the repository on each call, so it opens no store
 * until the host reads, and picks up a repository registered later.
 */
internal class KlaviyoMobileInboxProvider : MobileInboxProvider {

    override val mobileInbox: MobileInbox = object : MobileInbox {
        // Resolved when collected, like the calls, so a repository registered later is used
        override val changes: Flow<Unit> = flow { emitAll(repository.changes) }

        override suspend fun fetchPage(query: InboxQuery): InboxPage = repository.fetchPage(query)

        override suspend fun fetchUnreadCount(): Int = repository.fetchUnreadCount()

        override suspend fun updateMessageState(
            messageId: String,
            change: InboxStateChange
        ): Boolean = repository.updateMessageState(messageId, change)

        // Deliberately independent of the repository: a click is reported whether or not the
        // message is still stored, and never touches local message state.
        override fun trackMessageClick(
            message: InboxMessage,
            action: InboxAction?,
            element: InboxClickElement?
        ) {
            buildInboxClickEvent(message, action, element)?.let { Klaviyo.createEvent(it) }
        }
    }

    private val repository: InboxRepository get() = Registry.get()
}

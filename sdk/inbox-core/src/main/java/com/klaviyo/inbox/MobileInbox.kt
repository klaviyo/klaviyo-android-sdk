package com.klaviyo.inbox

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.core.Registry
import kotlinx.coroutines.flow.Flow

/**
 * Read access to the on-device inbox, and the local message state APIs.
 *
 * Every call is safe from the main thread. Calls succeed with an empty result when nothing has
 * been captured yet. A failure surfaces as [InboxException].
 */
interface MobileInbox {
    /**
     * Fetch one page of messages
     *
     * @throws InboxException with [InboxException.Reason.INVALID_CURSOR] when [InboxQuery.cursor]
     *  does not belong to this query
     */
    suspend fun fetchPage(query: InboxQuery = InboxQuery()): InboxPage

    /**
     * Count the current profile's messages that are unread and not archived
     */
    suspend fun fetchUnreadCount(): Int

    /**
     * Apply [change] to a message. Idempotent, and an unknown [messageId] does nothing.
     */
    suspend fun updateMessageState(messageId: String, change: InboxStateChange)

    /**
     * Emits whenever stored messages change: a message is captured, retention removes one, a
     * message's state changes, or messages are cleared. It carries no data. Refetch the page
     * being shown.
     *
     * Emissions are conflated, so several quick changes can arrive as one. Start collecting
     * before fetching, or a change in between is missed.
     */
    val changes: Flow<Unit>
}

/**
 * Access to the Mobile Inbox.
 *
 * @throws MissingKlaviyoModule if the `com.klaviyo:inbox` module is not on the classpath.
 */
val Klaviyo.mobileInbox: MobileInbox
    get() = Registry.getOrNull<MobileInboxProvider>()?.mobileInbox
        ?: throw MissingKlaviyoModule("inbox")

package com.klaviyo.inbox

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.core.Registry

/**
 * Access to the on-device inbox.
 *
 * Every call is safe from the main thread. A store failure surfaces as [InboxException].
 */
interface MobileInbox {
    /**
     * Fetch one page of the current company's messages from the on-device store, newest first.
     * The page is empty when nothing has been captured yet.
     *
     * @throws InboxException.InvalidCursor when the query's cursor is malformed or did not come
     *  from this inbox
     */
    suspend fun fetchPage(query: InboxQuery = InboxQuery()): InboxPage

    /**
     * Count the current company's messages that are unread and not archived. Zero when nothing
     * has been captured yet.
     */
    suspend fun fetchUnreadCount(): Int

    /**
     * Apply [change] to a message on this device. Applying a change a message already reflects
     * does nothing, so the call is idempotent.
     *
     * @return True when the current company has a message with [messageId], whether or not the
     *  change altered it. False when it doesn't, for example because retention removed it.
     */
    suspend fun updateMessageState(messageId: String, change: InboxStateChange): Boolean
}

/**
 * Access to the Mobile Inbox.
 *
 * @throws MissingKlaviyoModule if the `com.klaviyo:inbox` module is not on the classpath.
 */
val Klaviyo.mobileInbox: MobileInbox
    get() = Registry.getOrNull<MobileInboxProvider>()?.mobileInbox
        ?: throw MissingKlaviyoModule("inbox")

package com.klaviyo.inbox

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.core.Registry
import kotlinx.coroutines.flow.Flow

/**
 * Access to the on-device inbox.
 *
 * Every call is safe from the main thread. A store failure surfaces as [InboxException].
 */
interface MobileInbox {
    /**
     * Emits when the inbox's messages change: a push is captured, a message's state changes, or
     * messages are cleared. A host that shows the inbox fetches again when it emits.
     *
     * A signal, not a diff. It carries no data, and a burst of changes may arrive as one emission.
     * It can also fire for a change that doesn't affect what the host is showing, such as another
     * company's messages. Nothing is replayed, so collect first and then fetch, or a change in
     * between is missed. Collecting does not open the on-device store.
     */
    val changes: Flow<Unit>

    /**
     * Fetch one page of the current company's messages from the on-device store, newest first.
     * The query's filters choose which messages are returned, and archived messages are left out
     * unless it asks for them. The page is empty when nothing has been captured yet.
     *
     * @throws InboxException.InvalidCursor when the query's cursor is malformed, did not come from
     *  this inbox, or was returned for a query with different filters
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

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

    /**
     * Record that the user tapped [message], or one of its buttons, as a Clicked App Inbox
     * Message event for the current profile. The event is queued and delivered with the SDK's
     * usual retries, whether or not the message is still stored on this device.
     *
     * Clicking and message state are independent: this never marks the message read or archived,
     * and a state change never records a click. The SDK does not open the destination. Navigation
     * stays with the host.
     *
     * @param message The tapped message
     * @param action The tapped button, or null for a tap on the message itself
     * @param element Which part was tapped. Defaults to [InboxClickElement.BUTTON] when [action] is
     *  given, otherwise [InboxClickElement.BODY].
     */
    fun trackMessageClick(
        message: InboxMessage,
        action: InboxAction? = null,
        element: InboxClickElement? = null
    )
}

/**
 * Access to the Mobile Inbox.
 *
 * @throws MissingKlaviyoModule if the `com.klaviyo:inbox` module is not on the classpath.
 */
val Klaviyo.mobileInbox: MobileInbox
    get() = Registry.getOrNull<MobileInboxProvider>()?.mobileInbox
        ?: throw MissingKlaviyoModule("inbox")

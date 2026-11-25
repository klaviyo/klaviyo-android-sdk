package com.klaviyo.inbox

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.core.Registry
import java.io.Serializable
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
     * @param properties Extra properties to record on the event, such as which UI element the user
     *  tapped. They are added last, so a key that matches one of the SDK's own properties, such
     *  as `Action Type`, is ignored in favor of the SDK's value.
     */
    fun trackMessageClick(
        message: InboxMessage,
        action: InboxAction? = null,
        properties: Map<String, Serializable?> = emptyMap()
    )

    /**
     * Record a click on the stored message [messageId], or on one of its buttons, as
     * [trackMessageClick] does for a message the host already holds. For callers that keep only a
     * message ID, such as a wrapper SDK.
     *
     * Nothing is recorded when the message is no longer stored, since the stored push is what
     * the event is attributed from, or when [actionId] names no button on the message.
     *
     * @param messageId The tapped message's [InboxMessage.id]
     * @param actionId The tapped button's [InboxAction.id], or null for a tap on the message itself
     * @param properties Extra properties to record on the event. See the overload above.
     * @return True when the click was queued. False when the current company has no message with
     *  [messageId], the message has no button [actionId], or the SDK is not initialized.
     * @throws InboxException.StorageFailure when the on-device store fails
     */
    suspend fun trackMessageClick(
        messageId: String,
        actionId: String? = null,
        properties: Map<String, Serializable?> = emptyMap()
    ): Boolean

    /**
     * Apply [change] to every one of the current company's messages that matches [filter], for
     * example to mark every unread message as read. A message already in the state [change] asks
     * for is left as it is, so repeating the call changes nothing and returns 0.
     *
     * [InboxStateChange.UNARCHIVE] only matches archived messages, so a [filter] that leaves them
     * out, such as one with [InboxArchiveFilter.ACTIVE], matches nothing and returns 0. To unarchive,
     * ask for archived messages with `InboxFilter.where(InboxReadFilter.ALL, InboxArchiveFilter.ARCHIVED)`
     * or [InboxFilter.ALL_MESSAGES].
     *
     * @return How many messages the call changed. Messages already in the requested state are not
     *  counted. 0 before the SDK is initialized.
     */
    suspend fun updateMessageState(change: InboxStateChange, filter: InboxFilter): Int

    /**
     * Remove every message the current company has captured on this device, read and archived
     * alike, and leave every other company's messages in place.
     *
     * @return How many messages were removed. Zero before the SDK is initialized, or when the
     *  current company has none.
     * @throws InboxException.StorageFailure when the on-device store fails
     */
    suspend fun clearAll(): Int
}

/**
 * Access to the Mobile Inbox.
 *
 * @throws MissingKlaviyoModule if the `com.klaviyo:inbox` module is not on the classpath.
 */
val Klaviyo.mobileInbox: MobileInbox
    get() = Registry.getOrNull<MobileInboxProvider>()?.mobileInbox
        ?: throw MissingKlaviyoModule("inbox")

package com.klaviyo.inbox

import androidx.annotation.RestrictTo
import java.util.Objects

/**
 * Which page of the inbox to fetch. Pages run newest first, by send time or by the time this device
 * received the push when the send time is unknown.
 *
 * @param pageSize Messages per page, clamped to [MIN_PAGE_SIZE]..[MAX_PAGE_SIZE]
 * @property cursor [InboxPage.nextCursor] from the previous page, or null for the first page
 */
class InboxQuery @JvmOverloads constructor(
    pageSize: Int = DEFAULT_PAGE_SIZE,
    val cursor: InboxCursor? = null
) {
    val pageSize: Int = pageSize.coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE)

    override fun equals(other: Any?): Boolean =
        other is InboxQuery && pageSize == other.pageSize && cursor == other.cursor

    override fun hashCode(): Int = Objects.hash(pageSize, cursor)

    override fun toString(): String = "InboxQuery(pageSize=$pageSize, cursor=$cursor)"

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MIN_PAGE_SIZE = 1
        const val MAX_PAGE_SIZE = 100
    }
}

/**
 * @property nextCursor Pass in [InboxQuery.cursor] for the next page. Null on the last page.
 */
class InboxPage
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
constructor(
    val messages: List<InboxMessage>,
    val nextCursor: InboxCursor?
) {
    override fun equals(other: Any?): Boolean =
        other is InboxPage && messages == other.messages && nextCursor == other.nextCursor

    override fun hashCode(): Int = Objects.hash(messages, nextCursor)

    override fun toString(): String = "InboxPage(messages=$messages, nextCursor=$nextCursor)"
}

/**
 * An opaque position in the inbox. Hosts only pass back the one a page returned.
 */
class InboxCursor
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
constructor(
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    val token: String
) {
    override fun equals(other: Any?): Boolean = other is InboxCursor && token == other.token

    override fun hashCode(): Int = token.hashCode()

    override fun toString(): String = "InboxCursor($token)"
}

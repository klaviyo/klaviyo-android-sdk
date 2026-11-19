package com.klaviyo.inbox

import androidx.annotation.RestrictTo
import java.util.Objects

/**
 * Which messages are returned by [InboxQuery.readFilter]
 */
enum class InboxReadFilter {
    /** Read and unread messages */
    ALL,

    /** Unread messages */
    UNREAD,

    /** Read messages */
    READ
}

/**
 * Which messages are returned by [InboxQuery.archiveFilter]
 */
enum class InboxArchiveFilter {
    /** Messages that are not archived */
    ACTIVE,

    /** Archived messages */
    ARCHIVED,

    /** Archived and not archived messages */
    ALL
}

/**
 * Which page of the inbox to fetch. Pages run newest first, by send time or by the time this device
 * received the push when the send time is unknown.
 *
 * The filters narrow the messages the pages are made of. By default a page holds every message that
 * is not archived, read or unread.
 *
 * @param pageSize Messages per page, clamped to [MIN_PAGE_SIZE]..[MAX_PAGE_SIZE]
 * @property cursor [InboxPage.nextCursor] from the previous page, or null for the first page. A
 *  cursor only continues the query it came from, so a query with a cursor must carry the same
 *  filters as the one that returned it.
 * @property readFilter Whether read messages, unread messages or both are returned
 * @property archiveFilter Whether archived messages, messages that are not archived or both are
 *  returned
 */
class InboxQuery @JvmOverloads constructor(
    pageSize: Int = DEFAULT_PAGE_SIZE,
    val cursor: InboxCursor? = null,
    val readFilter: InboxReadFilter = InboxReadFilter.ALL,
    val archiveFilter: InboxArchiveFilter = InboxArchiveFilter.ACTIVE
) {
    val pageSize: Int = pageSize.coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE)

    override fun equals(other: Any?): Boolean = other is InboxQuery &&
        pageSize == other.pageSize &&
        cursor == other.cursor &&
        readFilter == other.readFilter &&
        archiveFilter == other.archiveFilter

    override fun hashCode(): Int = Objects.hash(pageSize, cursor, readFilter, archiveFilter)

    override fun toString(): String = "InboxQuery(pageSize=$pageSize, cursor=$cursor, " +
        "readFilter=$readFilter, archiveFilter=$archiveFilter)"

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

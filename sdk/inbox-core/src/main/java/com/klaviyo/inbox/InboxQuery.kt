package com.klaviyo.inbox

import androidx.annotation.RestrictTo
import java.util.Objects

enum class InboxReadFilter { ALL, READ, UNREAD }

enum class InboxArchiveFilter { UNARCHIVED, ARCHIVED, ALL }

/**
 * Messages are ordered by sort time: the send time, or the time this device received the push
 * when the send time is unknown.
 */
enum class InboxSortOrder { NEWEST_FIRST, OLDEST_FIRST }

/**
 * A change to one message's state. Each is idempotent: applying it to a message already in that
 * state does nothing. Read and archived are independent.
 */
enum class InboxStateChange { MARK_READ, MARK_UNREAD, ARCHIVE, UNARCHIVE }

/**
 * Which page of the inbox to fetch
 *
 * @param pageSize Messages per page, clamped to [MIN_PAGE_SIZE]..[MAX_PAGE_SIZE]
 * @property cursor [InboxPage.nextCursor] from the previous page of the same query, or null for the
 *  first page. A cursor only works with the filters and sort order it came from.
 */
class InboxQuery @JvmOverloads constructor(
    pageSize: Int = DEFAULT_PAGE_SIZE,
    val cursor: InboxCursor? = null,
    val readFilter: InboxReadFilter = InboxReadFilter.ALL,
    val archiveFilter: InboxArchiveFilter = InboxArchiveFilter.UNARCHIVED,
    val sortOrder: InboxSortOrder = InboxSortOrder.NEWEST_FIRST
) {
    val pageSize: Int = pageSize.coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE)

    override fun equals(other: Any?): Boolean = other is InboxQuery &&
        pageSize == other.pageSize &&
        cursor == other.cursor &&
        readFilter == other.readFilter &&
        archiveFilter == other.archiveFilter &&
        sortOrder == other.sortOrder

    override fun hashCode(): Int = Objects.hash(
        pageSize,
        cursor,
        readFilter,
        archiveFilter,
        sortOrder
    )

    override fun toString(): String = "InboxQuery(pageSize=$pageSize, cursor=$cursor, " +
        "readFilter=$readFilter, archiveFilter=$archiveFilter, sortOrder=$sortOrder)"

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
 * An opaque position in a query's results. Hosts must not parse or build one.
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

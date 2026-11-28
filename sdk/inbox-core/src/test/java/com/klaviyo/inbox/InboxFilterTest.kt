package com.klaviyo.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class InboxFilterTest {

    @Test
    fun `a filter carries the read and archive filters it was built with`() {
        val filter = InboxFilter.where(InboxReadFilter.UNREAD, InboxArchiveFilter.ARCHIVED)

        assertEquals(InboxReadFilter.UNREAD, filter.readFilter)
        assertEquals(InboxArchiveFilter.ARCHIVED, filter.archiveFilter)
    }

    @Test
    fun `all messages matches read and archived state alike`() {
        assertEquals(InboxReadFilter.ALL, InboxFilter.ALL_MESSAGES.readFilter)
        assertEquals(InboxArchiveFilter.ALL, InboxFilter.ALL_MESSAGES.archiveFilter)
        assertEquals(
            InboxFilter.where(InboxReadFilter.ALL, InboxArchiveFilter.ALL),
            InboxFilter.ALL_MESSAGES
        )
        assertSame(InboxFilter.ALL_MESSAGES, InboxFilter.ALL_MESSAGES)
    }

    @Test
    fun `filters with the same read and archive filters are equal`() {
        val a = InboxFilter.where(InboxReadFilter.READ, InboxArchiveFilter.ACTIVE)
        val b = InboxFilter.where(InboxReadFilter.READ, InboxArchiveFilter.ACTIVE)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `filters that differ in either filter are not equal`() {
        val base = InboxFilter.where(InboxReadFilter.READ, InboxArchiveFilter.ACTIVE)

        assertNotEquals(base, InboxFilter.where(InboxReadFilter.UNREAD, InboxArchiveFilter.ACTIVE))
        assertNotEquals(base, InboxFilter.where(InboxReadFilter.READ, InboxArchiveFilter.ARCHIVED))
        assertNotEquals(base, null)
    }

    @Test
    fun `every combination of filters is distinct`() {
        val all = InboxReadFilter.values().flatMap { read ->
            InboxArchiveFilter.values().map { archive -> InboxFilter.where(read, archive) }
        }

        assertEquals(9, all.toSet().size)
    }

    @Test
    fun `the string form names both filters`() {
        assertEquals(
            "InboxFilter(readFilter=UNREAD, archiveFilter=ALL)",
            InboxFilter.where(InboxReadFilter.UNREAD, InboxArchiveFilter.ALL).toString()
        )
    }
}

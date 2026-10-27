package com.klaviyo.inbox

import org.junit.Assert.assertEquals
import org.junit.Test

class InboxQueryTest {

    @Test
    fun `defaults are the first unarchived page, newest first`() {
        val query = InboxQuery()

        assertEquals(20, query.pageSize)
        assertEquals(null, query.cursor)
        assertEquals(InboxReadFilter.ALL, query.readFilter)
        assertEquals(InboxArchiveFilter.UNARCHIVED, query.archiveFilter)
        assertEquals(InboxSortOrder.NEWEST_FIRST, query.sortOrder)
    }

    @Test
    fun `page size is clamped`() {
        assertEquals(1, InboxQuery(pageSize = 0).pageSize)
        assertEquals(1, InboxQuery(pageSize = -5).pageSize)
        assertEquals(100, InboxQuery(pageSize = 101).pageSize)
        assertEquals(37, InboxQuery(pageSize = 37).pageSize)
    }

    @Test
    fun `queries with the same values are equal`() {
        assertEquals(InboxQuery(pageSize = 5), InboxQuery(pageSize = 5))
        assertEquals(InboxQuery(pageSize = 5).hashCode(), InboxQuery(pageSize = 5).hashCode())
    }

    @Test
    fun `raw value types compare by value`() {
        assertEquals(InboxDestinationType.DEEP_LINK, InboxDestinationType.fromRawValue("deep_link"))
        assertEquals("open_app", InboxDestinationType.OPEN_APP.rawValue)
        assertEquals("captured", InboxMessageSource.CAPTURED.rawValue)
    }
}

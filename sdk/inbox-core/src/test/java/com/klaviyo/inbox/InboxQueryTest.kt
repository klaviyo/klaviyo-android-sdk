package com.klaviyo.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class InboxQueryTest {

    @Test
    fun `a query defaults to the first page of the default size`() {
        val query = InboxQuery()

        assertEquals(InboxQuery.DEFAULT_PAGE_SIZE, query.pageSize)
        assertEquals(null, query.cursor)
    }

    @Test
    fun `page size is clamped to the supported range`() {
        assertEquals(InboxQuery.MIN_PAGE_SIZE, InboxQuery(pageSize = 0).pageSize)
        assertEquals(InboxQuery.MIN_PAGE_SIZE, InboxQuery(pageSize = -5).pageSize)
        assertEquals(InboxQuery.MAX_PAGE_SIZE, InboxQuery(pageSize = 10_000).pageSize)
        assertEquals(37, InboxQuery(pageSize = 37).pageSize)
    }

    @Test
    fun `queries are equal when page size and cursor match`() {
        val cursor = InboxCursor("token")

        assertEquals(InboxQuery(10, cursor), InboxQuery(10, InboxCursor("token")))
        assertEquals(InboxQuery(10, cursor).hashCode(), InboxQuery(10, cursor).hashCode())
        assertNotEquals(InboxQuery(10, cursor), InboxQuery(11, cursor))
        assertNotEquals(InboxQuery(10, cursor), InboxQuery(10, null))
    }
}

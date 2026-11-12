package com.klaviyo.inbox

import android.util.Base64
import com.klaviyo.fixtures.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

internal class InboxCursorCodecTest : BaseTest() {

    private val last = inboxMessageEntity(
        transmissionId = "tm-9",
        companyId = API_KEY,
        sentAt = 5_000L,
        receivedAt = 6_000L
    )

    private fun assertInvalid(cursor: InboxCursor) {
        val error = assertThrows(InboxException::class.java) {
            InboxCursorCodec.decode(InboxQuery(cursor = cursor), API_KEY)
        }
        assertTrue(error is InboxException.InvalidCursor)
    }

    private fun cursorJson(version: Int = 2, kind: String = "local", extra: String = "") =
        """{"v":$version,"k":"$kind","c":"$API_KEY","r":"ALL","a":"ACTIVE"$extra}"""

    private fun cursorOf(json: String) =
        InboxCursor(Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP))

    @Test
    fun `a query without a cursor starts at the beginning`() {
        assertNull(InboxCursorCodec.decode(InboxQuery(), API_KEY))
    }

    @Test
    fun `a cursor decodes to the last message's position`() {
        val cursor = InboxCursorCodec.encode(last)

        assertEquals(
            InboxRecordPosition(5_000L, "tm-9"),
            InboxCursorCodec.decode(InboxQuery(cursor = cursor), API_KEY)
        )
    }

    @Test
    fun `a cursor uses the sort time, which falls back to the received time`() {
        val unsent = last.copy(sentAt = null, sortTime = last.receivedAt)

        assertEquals(
            InboxRecordPosition(6_000L, "tm-9"),
            InboxCursorCodec.decode(InboxQuery(cursor = InboxCursorCodec.encode(unsent)), API_KEY)
        )
    }

    @Test
    fun `the page size can change between pages`() {
        val cursor = InboxCursorCodec.encode(last)

        assertEquals(
            InboxRecordPosition(5_000L, "tm-9"),
            InboxCursorCodec.decode(InboxQuery(pageSize = 50, cursor = cursor), API_KEY)
        )
    }

    @Test
    fun `malformed cursors are rejected`() {
        assertInvalid(InboxCursor("not a cursor"))
        assertInvalid(cursorOf("not json"))
        assertInvalid(cursorOf(cursorJson()))
        assertInvalid(cursorOf(cursorJson(extra = ""","t":1,"i":""""")))
    }

    @Test
    fun `a cursor from another version or source is rejected`() {
        val position = ""","t":1,"i":"a""""
        assertInvalid(cursorOf(cursorJson(version = 1, extra = position)))
        assertInvalid(cursorOf(cursorJson(version = 3, extra = position)))
        assertInvalid(cursorOf(cursorJson(kind = "server", extra = position)))
    }

    @Test
    fun `a cursor continues a query with the filters it was issued under`() {
        val query = InboxQuery(
            readFilter = InboxReadFilter.UNREAD,
            archiveFilter = InboxArchiveFilter.ALL
        )
        val cursor = InboxCursorCodec.encode(last, query)

        assertEquals(
            InboxRecordPosition(5_000L, "tm-9"),
            InboxCursorCodec.decode(
                InboxQuery(
                    cursor = cursor,
                    readFilter = InboxReadFilter.UNREAD,
                    archiveFilter = InboxArchiveFilter.ALL
                ),
                API_KEY
            )
        )
    }

    @Test
    fun `a cursor is rejected under a different read filter`() {
        val cursor = InboxCursorCodec.encode(last, InboxQuery(readFilter = InboxReadFilter.UNREAD))

        assertThrows(InboxException.InvalidCursor::class.java) {
            InboxCursorCodec.decode(InboxQuery(cursor = cursor), API_KEY)
        }
    }

    @Test
    fun `a cursor is rejected under a different archive filter`() {
        val cursor = InboxCursorCodec.encode(last)

        assertThrows(InboxException.InvalidCursor::class.java) {
            InboxCursorCodec.decode(
                InboxQuery(cursor = cursor, archiveFilter = InboxArchiveFilter.ARCHIVED),
                API_KEY
            )
        }
    }

    @Test
    fun `a cursor from another company is rejected`() {
        val cursor = InboxCursorCodec.encode(last.copy(companyId = "OtherCo"))

        assertInvalid(cursor)
    }
}

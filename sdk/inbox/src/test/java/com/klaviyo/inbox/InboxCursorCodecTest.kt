package com.klaviyo.inbox

import android.util.Base64
import com.klaviyo.fixtures.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

internal class InboxCursorCodecTest : BaseTest() {

    private val last = InboxMessageEntity(
        transmissionId = "tm-9",
        companyId = API_KEY,
        title = null,
        body = null,
        defaultDestination = InboxRecordDestination("open_app", null),
        mediaUrl = null,
        mediaType = null,
        customData = emptyMap(),
        actions = emptyList(),
        sentAt = 5_000L,
        collapseId = null,
        badge = null,
        receivedAt = 6_000L,
        attributionJson = null
    )

    private fun assertInvalid(cursor: InboxCursor) {
        val error = assertThrows(InboxException::class.java) {
            InboxCursorCodec.decode(InboxQuery(cursor = cursor))
        }
        assertEquals(InboxException.Reason.INVALID_CURSOR, error.reason)
    }

    private fun cursorOf(json: String) =
        InboxCursor(Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP))

    @Test
    fun `a query without a cursor starts at the beginning`() {
        assertNull(InboxCursorCodec.decode(InboxQuery()))
    }

    @Test
    fun `a cursor decodes to the last message's position`() {
        val cursor = InboxCursorCodec.encode(last)

        assertEquals(
            InboxRecordPosition(5_000L, "tm-9"),
            InboxCursorCodec.decode(InboxQuery(cursor = cursor))
        )
    }

    @Test
    fun `a cursor uses the sort time, which falls back to the received time`() {
        val unsent = last.copy(sentAt = null, sortTime = last.receivedAt)

        assertEquals(
            InboxRecordPosition(6_000L, "tm-9"),
            InboxCursorCodec.decode(InboxQuery(cursor = InboxCursorCodec.encode(unsent)))
        )
    }

    @Test
    fun `the page size can change between pages`() {
        val cursor = InboxCursorCodec.encode(last)

        assertEquals(
            InboxRecordPosition(5_000L, "tm-9"),
            InboxCursorCodec.decode(InboxQuery(pageSize = 50, cursor = cursor))
        )
    }

    @Test
    fun `malformed cursors are rejected`() {
        assertInvalid(InboxCursor("not a cursor"))
        assertInvalid(cursorOf("not json"))
        assertInvalid(cursorOf("""{"v":1,"k":"local"}"""))
        assertInvalid(cursorOf("""{"v":1,"k":"local","t":1,"i":""}"""))
    }

    @Test
    fun `a cursor from another version or source is rejected`() {
        assertInvalid(cursorOf("""{"v":2,"k":"local","t":1,"i":"a"}"""))
        assertInvalid(cursorOf("""{"v":1,"k":"server","t":1,"i":"a"}"""))
    }
}

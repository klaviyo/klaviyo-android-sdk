package com.klaviyo.inbox

import com.klaviyo.fixtures.BaseTest
import com.klaviyo.inbox.InboxServerFixtures.CONTENT_OBJECT
import com.klaviyo.inbox.InboxServerFixtures.CURSOR
import com.klaviyo.inbox.InboxServerFixtures.READ_AT_MS
import com.klaviyo.inbox.InboxServerFixtures.SENT_AT_MS
import com.klaviyo.inbox.InboxServerFixtures.TRANSMISSION_ID
import com.klaviyo.inbox.InboxServerFixtures.nextLink
import com.klaviyo.inbox.InboxServerFixtures.page
import com.klaviyo.inbox.InboxServerFixtures.pageWith
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class InboxServerParserTest : BaseTest() {

    private fun parse(body: String?, requestCursor: String? = null) =
        InboxServerParser.parsePage(body, requestCursor)

    private fun success(body: String?, requestCursor: String? = null): InboxServerPage =
        (parse(body, requestCursor) as InboxApiResult.Success).value

    private fun assertFailure(result: InboxApiResult<*>) =
        assertTrue("was $result", result is InboxApiResult.ProtocolFailure)

    @Test
    fun `a full message is parsed with every field`() {
        val message = success(
            pageWith(
                "is_read" to true,
                "read_updated_at" to "2026-10-15T12:05:00+00:00",
                "is_archived" to true,
                "archived_updated_at" to "2026-10-15T12:06:00+00:00",
                "updated_at" to "2026-10-15T12:07:00+00:00"
            )
        ).messages.single()

        assertEquals(TRANSMISSION_ID, message.transmissionId)
        assertEquals(SENT_AT_MS, message.sentAt)
        assertEquals(SENT_AT_MS + NINETY_DAYS_MS, message.expiresAt)
        assertEquals(true, message.isRead)
        assertEquals(READ_AT_MS, message.readUpdatedAt)
        assertEquals(true, message.isArchived)
        assertEquals(SENT_AT_MS + 360_000L, message.archivedUpdatedAt)
        assertEquals(SENT_AT_MS + 420_000L, message.updatedAt)
    }

    @Test
    fun `content is kept as raw JSON`() {
        val content = success(page()).messages.single().contentJson

        assertEquals(JSONObject(CONTENT_OBJECT).toString(), content)
    }

    @Test
    fun `null or absent content gives null`() {
        assertNull(success(pageWith("content" to null)).messages.single().contentJson)
        assertNull(success(pageWith(absent = listOf("content"))).messages.single().contentJson)
    }

    @Test
    fun `content that is not an object fails the page`() {
        assertFailure(parse(pageWith("content" to "text")))
        assertFailure(parse(pageWith("content" to 5)))
        assertFailure(parse(pageWith("content" to JSONArray())))
    }

    @Test
    fun `null or absent timestamps give null`() {
        val message = success(
            pageWith(
                "sent_at" to null,
                "expires_at" to null,
                absent = listOf("read_updated_at", "archived_updated_at", "updated_at")
            )
        ).messages.single()

        assertNull(message.sentAt)
        assertNull(message.expiresAt)
        assertNull(message.readUpdatedAt)
        assertNull(message.archivedUpdatedAt)
        assertNull(message.updatedAt)
    }

    @Test
    fun `updatedAt absent is allowed and a present value is parsed`() {
        assertNull(success(page()).messages.single().updatedAt)
        assertEquals(
            SENT_AT_MS,
            success(pageWith("updated_at" to "2026-10-15T12:00:00+00:00")).messages.single().updatedAt
        )
    }

    @Test
    fun `a present but unparseable timestamp fails the page`() {
        listOf("sent_at", "expires_at", "read_updated_at", "archived_updated_at", "updated_at").forEach {
            assertFailure(parse(pageWith(it to "yesterday")))
            assertFailure(parse(pageWith(it to 1_760_529_600)))
            assertFailure(parse(pageWith(it to "")))
        }
    }

    @Test
    fun `a bad updatedAt fails the page`() {
        assertFailure(parse(pageWith("updated_at" to "2026-13-45T99:00:00+00:00")))
    }

    @Test
    fun `missing or mistyped booleans fail the page`() {
        assertFailure(parse(pageWith(absent = listOf("is_read"))))
        assertFailure(parse(pageWith(absent = listOf("is_archived"))))
        assertFailure(parse(pageWith("is_read" to null)))
        assertFailure(parse(pageWith("is_read" to "true")))
        assertFailure(parse(pageWith("is_archived" to 1)))
    }

    @Test
    fun `a missing or blank id fails the page`() {
        assertFailure(parse(page(listOf(InboxServerFixtures.item(id = null)))))
        assertFailure(parse(page(listOf(InboxServerFixtures.item(id = "  ")))))
        assertFailure(parse(page(listOf(InboxServerFixtures.item(id = 12)))))
    }

    @Test
    fun `one bad item fails the whole page`() {
        val good = InboxServerFixtures.item()
        val bad = InboxServerFixtures.item(
            id = "tm-2",
            attributes = InboxServerFixtures.attributes("sent_at" to "garbage")
        )

        assertFailure(parse(page(listOf(good, bad))))
        assertFailure(parse(page(listOf(good, bad.put("attributes", JSONObject.NULL)))))
    }

    @Test
    fun `an item that is not an object fails the page`() {
        assertFailure(parse("""{"data":["tm-1"],"links":{"next":null}}"""))
    }

    @Test
    fun `a bad envelope fails the page`() {
        assertFailure(parse(null))
        assertFailure(parse(""))
        assertFailure(parse("<html>nope</html>"))
        assertFailure(parse("[]"))
        assertFailure(parse("{}"))
        assertFailure(parse("""{"data":{"id":"x"}}"""))
        assertFailure(parse("""{"data":null}"""))
        assertFailure(parse("""{"data":[],"links":"x"}"""))
    }

    @Test
    fun `unknown fields are ignored`() {
        val body = page().replace(
            "\"type\":\"inbox-message\"",
            "\"type\":\"inbox-message\",\"surprise\":{\"a\":1}"
        ).replace("\"is_read\":false", "\"is_read\":false,\"new_flag\":\"x\"")

        assertEquals(1, success(body).messages.size)
    }

    @Test
    fun `an empty page with a next link is a success`() {
        val result = success(page(emptyList(), next = nextLink(CURSOR)))

        assertTrue(result.messages.isEmpty())
        assertEquals(CURSOR, result.nextCursor)
    }

    @Test
    fun `an empty last page has no cursor`() {
        val result = success(page(emptyList()))

        assertTrue(result.messages.isEmpty())
        assertNull(result.nextCursor)
    }

    @Test
    fun `null or absent links is the last page`() {
        assertNull(success("""{"data":[]}""").nextCursor)
        assertNull(success("""{"data":[],"links":null}""").nextCursor)
        assertNull(success("""{"data":[],"links":{"next":null}}""").nextCursor)
        assertNull(success("""{"data":[],"links":{}}""").nextCursor)
    }

    @Test
    fun `the cursor is returned byte for byte in the encoded bracket form`() {
        assertEquals(CURSOR, success(page(next = nextLink(CURSOR))).nextCursor)
    }

    @Test
    fun `the cursor is returned byte for byte in the literal bracket form`() {
        assertEquals(
            CURSOR,
            success(page(next = nextLink(CURSOR, "page[cursor]"))).nextCursor
        )
    }

    @Test
    fun `a lowercase encoded bracket form is accepted`() {
        assertEquals(
            CURSOR,
            success(page(next = nextLink(CURSOR, "page%5bcursor%5d"))).nextCursor
        )
    }

    @Test
    fun `a cursor with plus, percent escapes and equals is never decoded`() {
        val raw = "a+b%2Bc%3D%3D=d"

        assertEquals(raw, success(page(next = nextLink(raw))).nextCursor)
    }

    @Test
    fun `a cursor in the middle of the query stops at the next parameter`() {
        val next = "https://a.klaviyo.com/api/inbox-messages?page%5Bcursor%5D=$CURSOR&platform=android"

        assertEquals(CURSOR, success(page(next = next)).nextCursor)
    }

    @Test
    fun `the host of the link is ignored`() {
        assertEquals(
            CURSOR,
            success(page(next = InboxServerFixtures.FOREIGN_NEXT_LINK)).nextCursor
        )
    }

    @Test
    fun `a next link without a cursor fails the page`() {
        assertFailure(
            parse(page(next = "https://a.klaviyo.com/api/inbox-messages?platform=android"))
        )
        assertFailure(
            parse(page(next = "https://a.klaviyo.com/api/inbox-messages?page%5Bcursor%5D="))
        )
        assertFailure(parse(page(next = "")))
        assertFailure(parse("""{"data":[],"links":{"next":5}}"""))
    }

    @Test
    fun `a size parameter alone is not mistaken for a cursor`() {
        assertFailure(parse(page(next = "https://a.klaviyo.com/x?page%5Bsize%5D=100")))
    }

    @Test
    fun `a cursor equal to the request cursor fails the page`() {
        assertFailure(parse(page(next = nextLink(CURSOR)), requestCursor = CURSOR))
    }

    @Test
    fun `a different cursor from the request cursor is accepted`() {
        assertEquals(
            CURSOR,
            success(page(next = nextLink(CURSOR)), requestCursor = "other").nextCursor
        )
    }

    @Test
    fun `accepted count equal to the number sent is a success`() {
        val result = InboxServerParser.parseAccepted(InboxServerFixtures.acceptedBody(2), 2)

        assertEquals(InboxApiResult.Success(2), result)
    }

    @Test
    fun `accepted count that is missing, fractional, mistyped or different fails`() {
        listOf(
            """{"data":{"attributes":{}}}""",
            """{"data":{}}""",
            """{}""",
            InboxServerFixtures.acceptedBody(2.5),
            InboxServerFixtures.acceptedBody(2.0),
            InboxServerFixtures.acceptedBody("\"2\""),
            InboxServerFixtures.acceptedBody("null"),
            InboxServerFixtures.acceptedBody(1),
            InboxServerFixtures.acceptedBody(3),
            InboxServerFixtures.acceptedBody(0),
            "not json"
        ).forEach {
            val result = InboxServerParser.parseAccepted(it, 2)
            assertTrue(it, result is InboxApiResult.ProtocolFailure)
            assertEquals(2, (result as InboxApiResult.ProtocolFailure).sent)
        }
        assertTrue(InboxServerParser.parseAccepted(null, 2) is InboxApiResult.ProtocolFailure)
    }

    @Test
    fun `a mismatched accepted count reports both numbers`() {
        val result = InboxServerParser.parseAccepted(InboxServerFixtures.acceptedBody(1), 2)

        assertEquals(InboxApiResult.ProtocolFailure("accepted_count mismatch", 2, 1), result)
    }

    @Test
    fun `error detail needs a JSON object with a non-empty errors array`() {
        assertEquals(
            "Invalid `Authorization` header value.",
            InboxServerParser.errorDetail(InboxServerFixtures.STRUCTURED_403)
        )
        assertEquals(
            "Forbidden",
            InboxServerParser.errorDetail("""{"errors":[{"title":"Forbidden"}]}""")
        )
        assertEquals("unspecified", InboxServerParser.errorDetail("""{"errors":[{}]}"""))
        // Only string values count, so an object or JSON null detail falls through to the title
        assertEquals(
            "Forbidden",
            InboxServerParser.errorDetail("""{"errors":[{"detail":{"x":1},"title":"Forbidden"}]}""")
        )
        assertEquals(
            "Forbidden",
            InboxServerParser.errorDetail("""{"errors":[{"detail":null,"title":"Forbidden"}]}""")
        )
        assertNull(InboxServerParser.errorDetail("""{"errors":[]}"""))
        assertNull(InboxServerParser.errorDetail("""{"message":"blocked"}"""))
        assertNull(InboxServerParser.errorDetail(InboxServerFixtures.CLOUDFLARE_403))
        assertNull(InboxServerParser.errorDetail("[]"))
        assertNull(InboxServerParser.errorDetail(null))
    }

    private companion object {
        const val NINETY_DAYS_MS = 90 * 24 * 3_600_000L
    }
}

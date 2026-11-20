package com.klaviyo.inbox

import com.klaviyo.core.Registry
import org.json.JSONException
import org.json.JSONObject

/** One page of the profile's inbox as the server sent it */
internal data class InboxServerPage(
    val messages: List<InboxServerMessage>,
    /** Raw, still percent-encoded `page[cursor]` value, or null on the last page */
    val nextCursor: String?
) {
    override fun toString(): String =
        "InboxServerPage(messages=$messages, hasNext=${nextCursor != null})"
}

/**
 * One message as the server sent it. Times are epoch milliseconds.
 * [contentJson] is the `content` object as raw JSON, kept out of [toString].
 */
internal data class InboxServerMessage(
    val transmissionId: String,
    val sentAt: Long?,
    val expiresAt: Long?,
    val isRead: Boolean,
    val readUpdatedAt: Long?,
    val isArchived: Boolean,
    val archivedUpdatedAt: Long?,
    val updatedAt: Long?,
    val contentJson: String?
) {
    override fun toString(): String =
        "InboxServerMessage(isRead=$isRead, isArchived=$isArchived, hasContent=${contentJson != null})"
}

/**
 * Parses Mobile Inbox responses. A malformed item fails the whole page, because a skipped item
 * would look like a server-side removal to the caller. Failure reasons are fixed text: org.json
 * exception messages quote the offending value, so they are never copied.
 */
internal object InboxServerParser {
    private const val DATA = "data"
    private const val ID = "id"
    private const val ATTRIBUTES = "attributes"
    private const val LINKS = "links"
    private const val NEXT = "next"
    private const val ERRORS = "errors"
    private const val SENT_AT = "sent_at"
    private const val EXPIRES_AT = "expires_at"
    private const val IS_READ = "is_read"
    private const val READ_UPDATED_AT = "read_updated_at"
    private const val IS_ARCHIVED = "is_archived"
    private const val ARCHIVED_UPDATED_AT = "archived_updated_at"
    private const val UPDATED_AT = "updated_at"
    private const val CONTENT = "content"
    private const val ACCEPTED_COUNT = "accepted_count"
    private const val DETAIL = "detail"
    private const val TITLE = "title"
    private const val CODE = "code"
    private const val UNSPECIFIED_DETAIL = "unspecified"
    private const val MAX_DETAIL_LENGTH = 200

    /**
     * The raw `page[cursor]` value of a link, in either bracket form. Never decoded.
     * The link's host and path are ignored.
     */
    private val CURSOR_PATTERN = Regex("""[?&]page(?:\[cursor]|%5[Bb]cursor%5[Dd])=([^&#]*)""")

    private class Malformed(val reason: String) : Exception(reason)

    private fun fail(reason: String): Nothing = throw Malformed(reason)

    /**
     * @param requestCursor The cursor the request used, to catch a link that points back at it
     */
    fun parsePage(body: String?, requestCursor: String?): InboxApiResult<InboxServerPage> = try {
        val root = JSONObject(body ?: fail("empty body"))
        val data = root.optJSONArray(DATA) ?: fail("data is not an array")
        val messages = List(data.length()) { index ->
            parseMessage(data.optJSONObject(index) ?: fail("item $index is not an object"), index)
        }
        InboxApiResult.Success(InboxServerPage(messages, nextCursor(root, requestCursor)))
    } catch (e: Malformed) {
        InboxApiResult.ProtocolFailure(e.reason)
    } catch (e: JSONException) {
        InboxApiResult.ProtocolFailure("malformed JSON")
    }

    /**
     * @param sent How many changes the request carried, which the server must have accepted
     */
    fun parseAccepted(body: String?, sent: Int): InboxApiResult<Int> = try {
        val count = JSONObject(body ?: fail("empty body"))
            .optJSONObject(DATA)
            ?.optJSONObject(ATTRIBUTES)
            ?.opt(ACCEPTED_COUNT)
        // Fractional counts parse as Double, which is rejected here
        val accepted = (count as? Int)?.toLong() ?: (count as? Long)
        when {
            accepted == null -> InboxApiResult.ProtocolFailure(
                "accepted_count missing or not an integer",
                sent
            )
            accepted != sent.toLong() ->
                InboxApiResult.ProtocolFailure("accepted_count mismatch", sent, accepted)
            else -> InboxApiResult.Success(sent)
        }
    } catch (e: Malformed) {
        InboxApiResult.ProtocolFailure(e.reason, sent)
    } catch (e: JSONException) {
        InboxApiResult.ProtocolFailure("malformed JSON", sent)
    }

    /**
     * Reads a JSON:API error body.
     *
     * @return The first error's detail, title or code, or null unless the body is a JSON object
     *   with a non-empty `errors` array. Only that shape is an API verdict, anything else is the edge.
     */
    fun errorDetail(body: String?): String? = try {
        val errors = body?.let { JSONObject(it) }?.optJSONArray(ERRORS)
        if (errors == null || errors.length() == 0) {
            null
        } else {
            val first = errors.optJSONObject(0)
            listOf(DETAIL, TITLE, CODE)
                .firstNotNullOfOrNull { key -> (first?.opt(key) as? String)?.takeIf { it.isNotBlank() } }
                ?.take(MAX_DETAIL_LENGTH)
                ?: UNSPECIFIED_DETAIL
        }
    } catch (e: JSONException) {
        null
    }

    private fun parseMessage(item: JSONObject, index: Int): InboxServerMessage {
        val id = (item.opt(ID) as? String)?.takeIf { it.isNotBlank() } ?: fail(
            "item $index has no id"
        )
        val attributes = item.optJSONObject(ATTRIBUTES) ?: fail("item $index has no attributes")
        return InboxServerMessage(
            transmissionId = id,
            sentAt = attributes.time(SENT_AT),
            expiresAt = attributes.time(EXPIRES_AT),
            isRead = attributes.flag(IS_READ),
            readUpdatedAt = attributes.time(READ_UPDATED_AT),
            isArchived = attributes.flag(IS_ARCHIVED),
            archivedUpdatedAt = attributes.time(ARCHIVED_UPDATED_AT),
            updatedAt = attributes.time(UPDATED_AT),
            contentJson = attributes.content()
        )
    }

    private fun JSONObject.flag(key: String): Boolean = opt(key) as? Boolean ?: fail(
        "$key is not a boolean"
    )

    /** Absent or JSON null is null. Present but not a parseable timestamp fails the page */
    private fun JSONObject.time(key: String): Long? {
        if (isNull(key)) return null
        val text = opt(key) as? String ?: fail("$key is not a string")
        return Registry.clock.parseRfc3339(text) ?: fail("$key is not a timestamp")
    }

    /** Absent or JSON null is null. An object is kept as raw JSON. Anything else fails the page */
    private fun JSONObject.content(): String? {
        if (isNull(CONTENT)) return null
        return (opt(CONTENT) as? JSONObject)?.toString() ?: fail("$CONTENT is not an object")
    }

    /** Absent or JSON null `links.next` is the last page. A link without a cursor is a failure */
    private fun nextCursor(root: JSONObject, requestCursor: String?): String? {
        if (root.isNull(LINKS)) return null
        val links = root.optJSONObject(LINKS) ?: fail("links is not an object")
        if (links.isNull(NEXT)) return null
        val next = links.opt(NEXT) as? String ?: fail("next is not a string")
        val cursor = CURSOR_PATTERN.find(next)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
            ?: fail("next has no cursor")
        if (cursor == requestCursor) fail("next cursor did not advance")
        return cursor
    }
}

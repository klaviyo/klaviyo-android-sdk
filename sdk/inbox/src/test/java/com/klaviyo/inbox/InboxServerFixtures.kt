package com.klaviyo.inbox

import com.klaviyo.analytics.networking.requests.InboxApiResponse
import org.json.JSONObject

/**
 * Responses shaped like the Mobile Inbox API, from the documented wire contract
 */
internal object InboxServerFixtures {
    const val PROFILE_ID = "01HPROFILE0000000000000000"
    const val TRANSMISSION_ID = "01HTRANSMISSION00000000000"

    /** Opaque, with the characters that break if a cursor is decoded and re-encoded */
    const val CURSOR = "eyJrIjoiYStiPT0ifQ%3D%3D%2Babc"

    /** `2026-10-15T12:00:00+00:00` */
    const val SENT_AT_MS = 1_792_065_600_000L

    /** `2026-10-15T12:05:00+00:00` */
    const val READ_AT_MS = 1_792_065_900_000L

    /** Title text from a sample message, which must never reach a log */
    const val CONTENT_TITLE = "Flash sale: 40% off everything"

    const val FOREIGN_NEXT_LINK =
        "https://evil.example.com/api/inbox-messages?profile_id=p&platform=android" +
            "&page%5Bsize%5D=100&page%5Bcursor%5D=$CURSOR"

    fun nextLink(cursor: String, bracketForm: String = "page%5Bcursor%5D") =
        "https://a.klaviyo.com/api/inbox-messages?profile_id=$PROFILE_ID&platform=android" +
            "&page%5Bsize%5D=100&$bracketForm=$cursor"

    const val CONTENT_OBJECT =
        """{"push_type":0,"title":"$CONTENT_TITLE","body":"Today only","deep_link_url_android":"app://sale"}"""

    /**
     * Attributes of an unread, unarchived message, with every timestamp the server sends.
     * A null override becomes an explicit JSON null, and [absent] keys are left out.
     */
    fun attributes(vararg overrides: Pair<String, Any?>, absent: List<String> = emptyList()): JSONObject =
        JSONObject().apply {
            put("sent_at", "2026-10-15T12:00:00+00:00")
            put("expires_at", "2027-01-13T12:00:00+00:00")
            put("is_read", false)
            put("read_updated_at", JSONObject.NULL)
            put("is_archived", false)
            put("archived_updated_at", JSONObject.NULL)
            put("content", JSONObject(CONTENT_OBJECT))
            overrides.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
            absent.forEach { remove(it) }
        }

    fun item(id: Any? = TRANSMISSION_ID, attributes: JSONObject = attributes()): JSONObject =
        JSONObject().put("type", "inbox-message").put("id", id ?: JSONObject.NULL)
            .put("attributes", attributes)

    fun page(items: List<JSONObject> = listOf(item()), next: String? = null): String =
        """{"data":[${items.joinToString(",")}],"links":{"self":"https://a.klaviyo.com/api/inbox-messages",""" +
            """"next":${next?.let { JSONObject.quote(it) }},"prev":null}}"""

    /** Whole-page response with an item whose `attributes` came from [attributes] */
    fun pageWith(vararg overrides: Pair<String, Any?>, absent: List<String> = emptyList()): String =
        page(listOf(item(attributes = attributes(*overrides, absent = absent))))

    fun response(code: Int?, body: String? = null, retryAfterMs: Long? = null, sent: Boolean = true) =
        InboxApiResponse(code, body, retryAfterMs, sent)

    fun ok(body: String) = response(200, body)

    fun acceptedBody(count: Any) =
        """{"data":{"type":"inbox-message-state-change","id":"b1c2","attributes":{"accepted_count":$count}}}"""

    const val STRUCTURED_403 =
        """{"errors":[{"id":"e1","status":403,"code":"forbidden","title":"Forbidden","detail":"Invalid `Authorization` header value."}]}"""

    const val STRUCTURED_400 =
        """{"errors":[{"id":"e2","status":400,"code":"invalid","title":"Invalid input","detail":"Invalid page cursor","source":{"parameter":"page[cursor]"}}]}"""

    const val CLOUDFLARE_403 =
        "<!DOCTYPE html><html><head><title>Attention Required! | Cloudflare</title></head><body>Sorry, you have been blocked</body></html>"
}

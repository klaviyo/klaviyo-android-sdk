package com.klaviyo.inbox

import android.util.Base64
import org.json.JSONObject

/**
 * Encodes a page position as the opaque [InboxCursor] hosts pass back.
 *
 * The token is versioned JSON holding the position of the last message on the page and a
 * fingerprint of the query's filters and sort order, so a cursor used with a different query
 * fails instead of returning the wrong rows. The kind says which source the position is in, so a
 * server position can share the type when messages are fetched from the server.
 */
internal object InboxCursorCodec {
    private const val VERSION = 1
    private const val LOCAL_KIND = "local"

    private const val VERSION_KEY = "v"
    private const val KIND_KEY = "k"
    private const val SORT_TIME_KEY = "t"
    private const val ID_KEY = "i"
    private const val QUERY_KEY = "q"

    private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    /**
     * @param last The last message on the page
     */
    fun encode(query: InboxQuery, last: InboxMessageEntity): InboxCursor {
        val json = JSONObject()
            .put(VERSION_KEY, VERSION)
            .put(KIND_KEY, LOCAL_KIND)
            .put(SORT_TIME_KEY, last.sortTime)
            .put(ID_KEY, last.transmissionId)
            .put(QUERY_KEY, fingerprint(query))
            .toString()
        return InboxCursor(Base64.encodeToString(json.toByteArray(Charsets.UTF_8), BASE64_FLAGS))
    }

    /**
     * @return The position the query's cursor points just past, or null when it has no cursor
     * @throws InboxException with [InboxException.Reason.INVALID_CURSOR] when the cursor is
     *  malformed or belongs to a different query
     */
    fun decode(query: InboxQuery): InboxRecordPosition? {
        val cursor = query.cursor ?: return null

        val json = try {
            JSONObject(String(Base64.decode(cursor.token, BASE64_FLAGS), Charsets.UTF_8))
        } catch (e: Exception) {
            throw invalid("The cursor could not be read", e)
        }

        if (json.optInt(VERSION_KEY) != VERSION || json.optString(KIND_KEY) != LOCAL_KIND) {
            throw invalid("The cursor is not from this inbox")
        }
        if (json.optString(QUERY_KEY) != fingerprint(query)) {
            throw invalid("The cursor belongs to a query with different filters or sort order")
        }

        val sortTime = json.optLong(SORT_TIME_KEY, Long.MIN_VALUE)
        val id = json.optString(ID_KEY)
        if (sortTime == Long.MIN_VALUE || id.isEmpty()) throw invalid("The cursor is incomplete")

        return InboxRecordPosition(sortTime, id)
    }

    private fun fingerprint(query: InboxQuery) =
        "${query.readFilter.name}|${query.archiveFilter.name}|${query.sortOrder.name}"

    private fun invalid(message: String, cause: Throwable? = null) =
        InboxException(InboxException.Reason.INVALID_CURSOR, message, cause)
}

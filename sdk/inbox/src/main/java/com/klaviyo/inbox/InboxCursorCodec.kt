package com.klaviyo.inbox

import android.util.Base64
import org.json.JSONObject

/**
 * Encodes a page position as the opaque [InboxCursor] hosts pass back.
 *
 * The token is versioned JSON holding the sort time and ID of the last message on the page, the
 * company the page was read for, and the query's filters. A cursor only continues a read for that
 * company with those filters, because a position under one set of filters says nothing about where
 * another set resumes. The kind says which source the position is in, so a server position can
 * share the type when messages are fetched from the server.
 */
internal object InboxCursorCodec {
    private const val VERSION = 2
    private const val LOCAL_KIND = "local"

    private const val VERSION_KEY = "v"
    private const val KIND_KEY = "k"
    private const val SORT_TIME_KEY = "t"
    private const val ID_KEY = "i"
    private const val COMPANY_KEY = "c"
    private const val READ_FILTER_KEY = "r"
    private const val ARCHIVE_FILTER_KEY = "a"

    private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    /**
     * @param last The last message on the page
     * @param query The query the page answered, whose filters the cursor is bound to
     */
    fun encode(last: InboxMessageEntity, query: InboxQuery = InboxQuery()): InboxCursor {
        val json = JSONObject()
            .put(VERSION_KEY, VERSION)
            .put(KIND_KEY, LOCAL_KIND)
            .put(SORT_TIME_KEY, last.sortTime)
            .put(ID_KEY, last.transmissionId)
            .put(COMPANY_KEY, last.companyId)
            .put(READ_FILTER_KEY, query.readFilter.name)
            .put(ARCHIVE_FILTER_KEY, query.archiveFilter.name)
            .toString()
        return InboxCursor(Base64.encodeToString(json.toByteArray(Charsets.UTF_8), BASE64_FLAGS))
    }

    /**
     * @param companyId The company the page is being read for
     * @return The position the query's cursor points just past, or null when it has no cursor
     * @throws InboxException.InvalidCursor when the cursor is malformed, or did not come from this
     *  inbox, this company or a query with these filters
     */
    fun decode(query: InboxQuery, companyId: String): InboxRecordPosition? {
        val cursor = query.cursor ?: return null

        val json = try {
            JSONObject(String(Base64.decode(cursor.token, BASE64_FLAGS), Charsets.UTF_8))
        } catch (e: Exception) {
            throw invalid("The cursor could not be read", e)
        }

        if (json.optInt(VERSION_KEY) != VERSION || json.optString(KIND_KEY) != LOCAL_KIND ||
            json.optString(COMPANY_KEY) != companyId
        ) {
            throw invalid("The cursor is not from this inbox")
        }

        if (json.optString(READ_FILTER_KEY) != query.readFilter.name ||
            json.optString(ARCHIVE_FILTER_KEY) != query.archiveFilter.name
        ) {
            throw invalid("The cursor is for a query with different filters")
        }

        val sortTime = json.optLong(SORT_TIME_KEY, Long.MIN_VALUE)
        val id = json.optString(ID_KEY)
        if (sortTime == Long.MIN_VALUE || id.isEmpty()) throw invalid("The cursor is incomplete")

        return InboxRecordPosition(sortTime, id)
    }

    private fun invalid(message: String, cause: Throwable? = null) =
        InboxException.InvalidCursor(message, cause)
}

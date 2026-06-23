package com.klaviyo.mobileInbox

import com.klaviyo.core.Registry
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

internal class InboxApiServiceImpl(
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    }
) : InboxApiService {

    private val baseUrl get() = Registry.config.baseUrl
    private val companyId get() = Registry.config.apiKey

    override suspend fun fetchMessages(profileParams: InboxProfileParams): List<InboxMessage> =
        withContext(Dispatchers.IO) {
            val all = mutableListOf<InboxMessage>()
            var nextUrl: String? = buildFetchUrl(
                profileParams,
                pageSize = PAGE_SIZE,
                pageCursor = null
            )

            while (nextUrl != null) {
                try {
                    val (messages, cursor) = fetchPage(URL(nextUrl))
                    all.addAll(messages)
                    nextUrl = cursor
                } catch (e: IOException) {
                    Registry.log.error("Inbox fetch failed with IO error", e)
                    break
                } catch (e: JSONException) {
                    Registry.log.error("Inbox response JSON parse failed", e)
                    break
                }
            }
            all
        }

    override suspend fun reportState(
        messageId: String,
        state: InboxServerState,
        profileParams: InboxProfileParams
    ) = withContext(Dispatchers.IO) {
        val url = URL("$baseUrl$MESSAGES_PATH/$messageId?${companyIdParam()}")
        val body = buildReportStateBody(messageId, state, profileParams)
        try {
            patch(url, body)
        } catch (e: IOException) {
            Registry.log.error("Inbox state report failed for $messageId", e)
        }
    }

    override suspend fun reportStateBulk(
        updates: List<InboxStateUpdate>,
        profileParams: InboxProfileParams
    ) = withContext(Dispatchers.IO) {
        if (updates.isEmpty()) return@withContext
        val url = URL("$baseUrl$BULK_PATH?${companyIdParam()}")
        val body = buildBulkReportBody(updates, profileParams)
        try {
            post(url, body)
        } catch (e: IOException) {
            Registry.log.error("Inbox bulk state report failed", e)
        }
    }

    // region Fetch helpers

    private fun fetchPage(url: URL): Pair<List<InboxMessage>, String?> {
        Registry.log.verbose("Fetching inbox page from $url")
        val connection = connectionFactory(url)
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty(HEADER_REVISION, REVISION)
            connection.setRequestProperty(HEADER_MOBILE, "1")
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Registry.log.warning("Inbox fetch returned HTTP $responseCode")
                return Pair(emptyList(), null)
            }

            val responseBody = connection.inputStream.bufferedReader().readText()
            parseListResponse(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseListResponse(json: String): Pair<List<InboxMessage>, String?> {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val messages = List(data.length()) { i ->
            val item = data.getJSONObject(i)
            val attrs = item.getJSONObject("attributes")
            InboxMessage(
                id = item.getString("id"),
                timestamp = parseIso8601(attrs.getString("created")),
                title = attrs.optString("title"),
                body = attrs.optString("body"),
                status = InboxStatus.UNREAD,
                source = InboxSource.REMOTE,
                pushTied = attrs.optBoolean("push_tied", false)
            )
        }
        val nextUrl = root.optJSONObject("links")?.takeIf { !it.isNull("next") }?.optString("next")
        return Pair(messages, nextUrl?.takeIf { it.isNotBlank() })
    }

    private fun buildFetchUrl(
        params: InboxProfileParams,
        pageSize: Int,
        pageCursor: String?
    ): String {
        val query = mutableListOf(companyIdParam(), "page_size=$pageSize")
        params.anonymousId?.let { query += "anonymous_id=${encode(it)}" }
        params.email?.let { query += "email=${encode(it)}" }
        params.externalId?.let { query += "external_id=${encode(it)}" }
        params.phoneNumber?.let { query += "phone_number=${encode(it)}" }
        pageCursor?.let { query += "page_cursor=${encode(it)}" }
        return "$baseUrl$MESSAGES_PATH?${query.joinToString("&")}"
    }

    // endregion

    // region Write helpers

    private fun patch(url: URL, body: String) {
        val connection = connectionFactory(url)
        try {
            connection.requestMethod = "POST" // some older HTTP clients don't support PATCH
            connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            writeJsonBody(connection, body)
            checkWriteResponse(connection, "PATCH $url")
        } finally {
            connection.disconnect()
        }
    }

    private fun post(url: URL, body: String) {
        val connection = connectionFactory(url)
        try {
            connection.requestMethod = "POST"
            writeJsonBody(connection, body)
            checkWriteResponse(connection, "POST $url")
        } finally {
            connection.disconnect()
        }
    }

    private fun writeJsonBody(connection: HttpURLConnection, body: String) {
        connection.doOutput = true
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty(HEADER_REVISION, REVISION)
        connection.setRequestProperty(HEADER_MOBILE, "1")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connect()
        OutputStreamWriter(connection.outputStream).use { it.write(body) }
    }

    private fun checkWriteResponse(connection: HttpURLConnection, context: String) {
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_ACCEPTED) {
            Registry.log.warning("Inbox write returned HTTP $code for $context")
        }
    }

    private fun buildReportStateBody(
        messageId: String,
        state: InboxServerState,
        profileParams: InboxProfileParams
    ): String = JSONObject().apply {
        put(
            "data",
            JSONObject().apply {
                put("type", "inbox-message")
                put("id", messageId)
                put(
                    "attributes",
                    JSONObject().apply {
                        put("state", state.name)
                        put("profile", buildProfilePayload(profileParams))
                    }
                )
            }
        )
    }.toString()

    private fun buildBulkReportBody(
        updates: List<InboxStateUpdate>,
        profileParams: InboxProfileParams
    ): String = JSONObject().apply {
        put(
            "data",
            JSONObject().apply {
                put("type", "inbox-message-bulk-update")
                put(
                    "attributes",
                    JSONObject().apply {
                        put(
                            "updates",
                            org.json.JSONArray().also { arr ->
                                updates.forEach { update ->
                                    arr.put(
                                        JSONObject().apply {
                                            put("id", update.id)
                                            put("state", update.state.name)
                                        }
                                    )
                                }
                            }
                        )
                        put("profile", buildProfilePayload(profileParams))
                    }
                )
            }
        )
    }.toString()

    private fun buildProfilePayload(params: InboxProfileParams): JSONObject = JSONObject().apply {
        put(
            "data",
            JSONObject().apply {
                put("type", "profile")
                put(
                    "attributes",
                    JSONObject().apply {
                        params.anonymousId?.let { put("anonymous_id", it) }
                        params.email?.let { put("email", it) }
                        params.externalId?.let { put("external_id", it) }
                        params.phoneNumber?.let { put("phone_number", it) }
                    }
                )
            }
        )
    }

    // endregion

    private fun companyIdParam() = "company_id=${encode(companyId)}"

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun parseIso8601(value: String): Long =
        runCatching { isoFormat.parse(value)?.time ?: 0L }
            .onFailure { Registry.log.warning("Failed to parse inbox timestamp: $value") }
            .getOrDefault(0L)

    companion object {
        private const val MESSAGES_PATH = "/client/inbox-messages"
        private const val BULK_PATH = "/client/inbox-message-bulk-update"
        private const val REVISION = "2026-07-15.pre"
        private const val HEADER_REVISION = "revision"
        private const val HEADER_MOBILE = "X-Klaviyo-Mobile"
        private const val PAGE_SIZE = 100
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000

        private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

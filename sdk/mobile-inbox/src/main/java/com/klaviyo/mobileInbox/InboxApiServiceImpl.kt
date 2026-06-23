package com.klaviyo.mobileInbox

import com.klaviyo.core.Registry
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

internal class InboxApiServiceImpl(
    private val endpointUrl: String,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    }
) : InboxApiService {

    override suspend fun fetchMessages(profileParams: InboxProfileParams): List<InboxMessage> =
        withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(profileParams)
                Registry.log.verbose("Fetching inbox messages from $url")
                val connection = connectionFactory(url)
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.connect()

                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        Registry.log.warning("Inbox fetch returned HTTP $responseCode")
                        return@withContext emptyList()
                    }

                    val body = connection.inputStream.bufferedReader().readText()
                    parseResponse(body)
                } finally {
                    connection.disconnect()
                }
            } catch (e: IOException) {
                Registry.log.error("Inbox fetch failed with IO error", e)
                emptyList()
            } catch (e: JSONException) {
                Registry.log.error("Inbox response JSON parse failed", e)
                emptyList()
            }
        }

    private fun buildUrl(params: InboxProfileParams): URL {
        val sb = StringBuilder(endpointUrl)
        val queryParams = mutableListOf<String>()
        params.anonymousId?.let { queryParams += "anonymous_id=${encode(it)}" }
        params.email?.let { queryParams += "email=${encode(it)}" }
        params.phoneNumber?.let { queryParams += "phone_number=${encode(it)}" }
        if (queryParams.isNotEmpty()) {
            sb.append('?')
            sb.append(queryParams.joinToString("&"))
        }
        return URL(sb.toString())
    }

    private fun parseResponse(json: String): List<InboxMessage> {
        val array = JSONArray(json)
        return List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            InboxMessage(
                id = obj.getString("id"),
                timestamp = obj.getLong("timestamp"),
                title = obj.getString("title"),
                body = obj.getString("body"),
                status = InboxStatus.UNREAD,
                source = InboxSource.REMOTE
            )
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }
}

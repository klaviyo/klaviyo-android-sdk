package com.klaviyo.analytics.networking.requests

import com.klaviyo.core.Registry
import com.klaviyo.core.config.NetworkRequest
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import org.json.JSONException
import org.json.JSONObject

/**
 * Base class for structuring and sending a Klaviyo API request
 *
 * @property urlPath URL this request will be connecting to as string
 * @property method The [RequestMethod] will determine the type of request being made
 * @property time The wall clock time that the request was enqueued
 * @property uuid unique identifier of this request
 */
internal open class KlaviyoApiRequest(
    val urlPath: String,
    val method: RequestMethod,
    val time: String = Registry.clock.currentTimeAsString(),
    val uuid: String = UUID.randomUUID().toString()
) : NetworkRequest {
    internal enum class Status {
        Unsent, PendingRetry, Complete, Failed
    }

    override val start_time: String = time
    override var end_time: String? = null

    override var headers: Map<String, String> = emptyMap()
    override var query: Map<String, String> = emptyMap()
    open var body: JSONObject? = null

    var attempts = 0
        private set

    protected var status: Status = Status.Unsent
        set(value) {
            field = value

            end_time = when (status) {
                Status.Complete, Status.Failed -> Registry.clock.currentTimeAsString()
                else -> null
            }
        }

    protected var response: String? = null

    /**
     * Creates a representation of this [KlaviyoApiRequest] in JSON
     *
     * NOTE: subclass information is lost in the process.
     * The important data to send the API request is retained.
     *
     * @return
     */
    fun toJson(): String = JSONObject()
        .accumulate(PATH_JSON_KEY, urlPath)
        .accumulate(METHOD_JSON_KEY, method.name)
        .accumulate(TIME_JSON_KEY, time)
        .accumulate(UUID_JSON_KEY, uuid)
        .accumulate(HEADERS_JSON_KEY, headers)
        .accumulate(QUERY_JSON_KEY, query)
        .accumulate(BODY_JSON_KEY, body)
        .toString()

    override val httpMethod: String get() = method.name
    override val state: String get() = status.name
    override fun formatBody(): String? = body?.let { JSONObject(mapOf(DATA to it)).toString() }
    override fun formatResponse(): String? = response
    override fun toString(): String = toJson()

    /**
     * To facilitate deduplication, we will treat UUID as a unique identifier
     * such that even if a request is deserialized from JSON it is still
     * "equal" to its original object, regardless of instance or subclass
     *
     * @param other
     * @return
     */
    override fun equals(other: Any?): Boolean = when (other) {
        is KlaviyoApiRequest -> uuid == other.uuid
        else -> super.equals(other)
    }

    override fun hashCode(): Int = uuid.hashCode()

    companion object {
        // Common keywords
        const val DATA = "data"
        const val TYPE = "type"
        const val ATTRIBUTES = "attributes"
        const val PROPERTIES = "properties"
        const val COMPANY_ID = "company_id"
        const val PROFILE = "profile"
        const val EVENT = "event"

        // Headers
        const val HEADER_CONTENT = "Content-Type"
        const val HEADER_ACCEPT = "Accept"
        const val HEADER_REVISION = "Revision"
        const val TYPE_JSON = "application/json"
        const val V3_REVISION = "2023-01-24"

        private const val HTTP_OK = HttpURLConnection.HTTP_OK
        private const val HTTP_MULT_CHOICE = HttpURLConnection.HTTP_MULT_CHOICE
        private const val HTTP_RETRY = 429 // oddly not a const in HttpURLConnection

        // JSON keys for persistence
        private const val PATH_JSON_KEY = "url_path"
        private const val METHOD_JSON_KEY = "method"
        private const val TIME_JSON_KEY = "time"
        private const val UUID_JSON_KEY = "uuid"
        private const val HEADERS_JSON_KEY = "headers"
        private const val QUERY_JSON_KEY = "query"
        private const val BODY_JSON_KEY = "body"

        /**
         * Construct a request from a JSON object
         *
         * Returns [KlaviyoApiRequest] no matter what subclass it was before encoding.
         * This is functionally the same and saves us some trouble
         *
         * @param json
         * @return
         * @throws JSONException If required fields are missing or improperly formatted
         */
        fun fromJson(json: JSONObject): KlaviyoApiRequest {
            // TODO - restore to child class to optimize storage space!
            val urlPath = json.getString(PATH_JSON_KEY)
            val method = when (json.getString(METHOD_JSON_KEY)) {
                RequestMethod.POST.name -> RequestMethod.POST
                else -> RequestMethod.GET
            }
            val time = json.getString(TIME_JSON_KEY)
            val uuid = json.getString(UUID_JSON_KEY)

            return KlaviyoApiRequest(urlPath, method, time, uuid).apply {
                headers = json.getJSONObject(HEADERS_JSON_KEY).let {
                    it.keys().asSequence().associateWith { k -> it.getString(k) }
                }
                query = json.getJSONObject(QUERY_JSON_KEY).let {
                    it.keys().asSequence().associateWith { k -> it.getString(k) }
                }
                body = json.optJSONObject(BODY_JSON_KEY)
            }
        }

        /**
         * Helper function to format the body of the request
         * Improves readability in subclasses
         *
         * @param K
         * @param V
         * @param pairs
         */
        fun <K, V> jsonMapOf(vararg pairs: Pair<K, V>) = JSONObject(pairs.toMap())

        /**
         * Helper function to create a map that filters out empty/null values
         *
         * @param K
         * @param V
         * @param pairs
         * @return
         */
        fun <K, V> filteredMapOf(
            vararg pairs: Pair<K, V>,
            allowEmptyMaps: Boolean = false
        ): Map<K, V> = pairs.toMap().filter { entry ->
            when (val value = entry.value) {
                is Map<*, *> -> allowEmptyMaps || value.isNotEmpty()
                is String -> value.isNotEmpty()
                else -> value != null
            }
        }
    }

    /**
     * Compiles the base url, path and query data into a [URL] object
     */
    override val url: URL
        get() {
            val baseUrl = Registry.config.baseUrl
            val queryMap = query.map { (key, value) -> "$key=$value" }
            val queryString = queryMap.joinToString(separator = "&")

            return if (queryString.isEmpty()) {
                URL("$baseUrl/$urlPath")
            } else {
                URL("$baseUrl/$urlPath?$queryString")
            }
        }

    /**
     * Builds and sends a network request to Klaviyo and then parses and handles the response
     *
     * @returns The string value of the response body, if one was returned
     */
    fun send(): Status {
        if (!Registry.networkMonitor.isNetworkConnected()) {
            return status
        }

        return try {
            val connection = buildUrlConnection()

            try {
                attempts++
                connection.connect()
                parseResponse(connection)
            } finally {
                connection.disconnect()
            }
        } catch (ex: IOException) {
            status = Status.Failed
            status
        }
    }

    /**
     * Opens a connection against the given [URL]
     * Connection type is either [HttpURLConnection] or [HttpsURLConnection] depending on the [URL] protocol
     */
    private fun buildUrlConnection(): HttpURLConnection {
        val connection = HttpUtil.openConnection(url)

        headers.forEach { (key, header) ->
            connection.setRequestProperty(key, header)
        }

        connection.requestMethod = method.name
        connection.readTimeout = Registry.config.networkTimeout
        connection.connectTimeout = Registry.config.networkTimeout

        val bodyString = formatBody() ?: return connection

        connection.doOutput = true
        HttpUtil.writeToConnection(bodyString, connection)

        return connection
    }

    /**
     * Parse and save the response code and body from the open [HttpURLConnection]
     *
     * If the request was successful, extracts the response body
     * If the request was unsuccessful, extracts the error response body
     *
     * [Docs](https://developers.klaviyo.com/en/docs/rate_limits_and_error_handling)
     *
     * @return The status of the request
     */
    protected open fun parseResponse(connection: HttpURLConnection): Status {
        // https://developers.klaviyo.com/en/docs/rate_limits_and_error_handling
        status = when (connection.responseCode) {
            in HTTP_OK until HTTP_MULT_CHOICE -> Status.Complete
            HTTP_RETRY -> {
                if (attempts <= Registry.config.networkMaxRetries) {
                    Status.PendingRetry
                } else {
                    Status.Failed
                }
            }
            // TODO - Special handling of unauthorized i.e. 401 and 403?
            // TODO - Special handling of server errors 500 and 503?
            else -> Status.Failed
        }

        val stream = when (status) {
            Status.Complete -> connection.inputStream
            else -> connection.errorStream
        }

        response = BufferedReader(InputStreamReader(stream)).use { it.readText() }

        return status
    }
}

package com.klaviyo.analytics.networking.requests

import com.klaviyo.core.Registry
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
 * @property queuedTime The wall clock time that the request was enqueued
 * @property uuid unique identifier of this request
 *
 * @constructor
 */
internal open class KlaviyoApiRequest(
    val urlPath: String,
    val method: RequestMethod,
    queuedTime: Long? = null,
    uuid: String? = null
) : ApiRequest {

    internal enum class Status {
        Unsent, Inflight, PendingRetry, Complete, Failed
    }

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
        private const val TYPE_JSON_KEY = "request_type"
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
         * @param json JSONObject to decode
         * @return Request object of original subclass type
         * @throws JSONException If required fields are missing or improperly formatted
         */
        fun fromJson(json: JSONObject): KlaviyoApiRequest {
            val urlPath = json.getString(PATH_JSON_KEY)
            val method = when (json.getString(METHOD_JSON_KEY)) {
                RequestMethod.POST.name -> RequestMethod.POST
                else -> RequestMethod.GET
            }
            val time = json.getLong(TIME_JSON_KEY)
            val uuid = json.getString(UUID_JSON_KEY)

            return when (json.optString(TYPE_JSON_KEY)) {
                ProfileApiRequest::class.simpleName -> ProfileApiRequest(time, uuid)
                EventApiRequest::class.simpleName -> EventApiRequest(time, uuid)
                PushTokenApiRequest::class.simpleName -> PushTokenApiRequest(time, uuid)
                else -> KlaviyoApiRequest(urlPath, method, time, uuid)
            }.apply {
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
     * Local unique identifier of this request for keyed storage
     */
    override val uuid: String = uuid ?: UUID.randomUUID().toString()

    /**
     * Descriptive title of this request, e.g. for debugging
     */
    override val title: String = urlPath

    /**
     * String representation of the current request state
     */
    override val state: String get() = status.name

    /**
     * Internal tracking of the request status
     * When status changes, this setter updates start and end timestamps
     */
    protected var status: Status = Status.Unsent
        set(value) {
            if (field == value) return
            field = value

            if (value == Status.Inflight) {
                startTime = Registry.clock.currentTimeMillis()
            } else if (status in arrayOf(Status.Complete, Status.Failed)) {
                endTime = Registry.clock.currentTimeMillis()
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
     * String representation of HTTP method
     */
    override val httpMethod: String = method.name

    /**
     * HTTP request headers
     */
    override var headers: Map<String, String> = emptyMap()

    /**
     * HTTP request query params
     */
    override var query: Map<String, String> = emptyMap()

    /**
     * JSON request body
     */
    open var body: JSONObject? = null

    /**
     * Convert request body to string
     */
    override val requestBody: String? get() = body?.toString()

    /**
     * Tracks number of attempts to limit retries
     */
    var attempts = 0
        private set

    /**
     * Timestamp request was first enqueued
     */
    override val queuedTime: Long = queuedTime ?: Registry.clock.currentTimeMillis()

    /**
     * Timestamp of latest send attempt
     */
    override var startTime: Long? = null
        protected set

    /**
     * Timestamp of latest send attempt completion
     */
    override var endTime: Long? = null
        protected set

    /**
     * HTTP Status code from last send attempt
     * null if unsent
     */
    override var responseCode: Int? = null
        protected set

    /**
     * Body of response content from last send attempt
     */
    override var responseBody: String? = null
        protected set

    /**
     * Creates a representation of this [KlaviyoApiRequest] in JSON
     *
     * @return A JSON representation that can be deserialized back into an API request object
     */
    fun toJson(): JSONObject = JSONObject()
        .accumulate(TYPE_JSON_KEY, this::class.simpleName)
        .accumulate(PATH_JSON_KEY, urlPath)
        .accumulate(METHOD_JSON_KEY, method.name)
        .accumulate(TIME_JSON_KEY, queuedTime)
        .accumulate(UUID_JSON_KEY, uuid)
        .accumulate(HEADERS_JSON_KEY, JSONObject(headers))
        .accumulate(QUERY_JSON_KEY, JSONObject(query))
        .accumulate(BODY_JSON_KEY, body)

    /**
     * For consistency, format as JSON when representing API requests as string
     */
    final override fun toString(): String = toJson().toString()

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

    /**
     * @return UUID as hashcode for equality comparisons
     */
    override fun hashCode(): Int = uuid.hashCode()

    /**
     * Builds and sends a network request to Klaviyo and then parses and handles the response
     *
     * @returns The string value of the response body, if one was returned
     */
    fun send(beforeSend: () -> Unit = { }): Status {
        if (!Registry.networkMonitor.isNetworkConnected()) {
            Registry.log.debug("Send prevented while network unavailable")
            return status
        }

        status = Status.Inflight

        return try {
            val connection = buildUrlConnection()

            try {
                attempts++
                beforeSend.invoke()
                connection.connect()
                parseResponse(connection)
            } finally {
                connection.disconnect()
            }
        } catch (ex: IOException) {
            Registry.log.error(ex.message ?: "", ex)
            status = Status.Failed
            status
        }
    }

    /**
     * Opens a connection against the given [URL]
     * Connection type is either [HttpURLConnection] or
     * [HttpsURLConnection] depending on the [URL] protocol
     */
    private fun buildUrlConnection(): HttpURLConnection {
        val connection = HttpUtil.openConnection(url)

        headers.forEach { (key, header) ->
            connection.setRequestProperty(key, header)
        }

        connection.requestMethod = method.name
        connection.readTimeout = Registry.config.networkTimeout
        connection.connectTimeout = Registry.config.networkTimeout

        requestBody?.let {
            connection.doOutput = true
            HttpUtil.writeToConnection(it, connection)
        }

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
        responseCode = connection.responseCode

        status = when (responseCode) {
            // TODO only 202 is a success though...
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

        responseBody = BufferedReader(InputStreamReader(stream)).use { it.readText() }

        return status
    }
}

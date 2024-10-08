package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.DeviceProperties
import com.klaviyo.core.Registry
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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
        const val PUSH_TOKEN = "push-token"
        const val UNREGISTER_PUSH_TOKEN = "push-token-unregister"

        // Headers
        const val HEADER_CONTENT = "Content-Type"
        const val HEADER_USER_AGENT = "User-Agent"
        const val HEADER_ACCEPT = "Accept"
        const val HEADER_REVISION = "Revision"
        const val HEADER_KLAVIYO_MOBILE = "X-Klaviyo-Mobile"
        const val HEADER_KLAVIYO_ATTEMPT = "X-Klaviyo-Attempt-Count"
        const val HEADER_RETRY_AFTER = "Retry-After"
        const val TYPE_JSON = "application/json"

        const val HTTP_OK = HttpURLConnection.HTTP_OK
        const val HTTP_ACCEPTED = HttpURLConnection.HTTP_ACCEPTED
        const val HTTP_MULT_CHOICE = HttpURLConnection.HTTP_MULT_CHOICE
        const val HTTP_RETRY = 429 // oddly not a const in HttpURLConnection
        const val HTTP_UNAVAILABLE = HttpURLConnection.HTTP_UNAVAILABLE
        const val HTTP_BAD_REQUEST = HttpURLConnection.HTTP_BAD_REQUEST

        // JSON keys for persistence
        const val TYPE_JSON_KEY = "request_type"
        const val PATH_JSON_KEY = "url_path"
        const val METHOD_JSON_KEY = "method"
        const val TIME_JSON_KEY = "time"
        const val UUID_JSON_KEY = "uuid"
        const val HEADERS_JSON_KEY = "headers"
        const val QUERY_JSON_KEY = "query"
        const val BODY_JSON_KEY = "body"

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
    override val type: String = urlPath

    /**
     * String representation of the current request state
     */
    override val state: String get() = status.name

    /**
     * Internal tracking of the request status
     * When status changes, this setter updates start and end timestamps
     */
    var status: Status = Status.Unsent
        private set(value) {
            if (field == value) return
            field = value

            if (value == Status.Inflight) {
                startTime = Registry.clock.currentTimeMillis()
            } else if (status in arrayOf(Status.Complete, Status.Failed)) {
                endTime = Registry.clock.currentTimeMillis()
            }
        }

    /**
     * Tracks number of attempts to limit retries
     */
    final override var attempts = 0
        private set(value) {
            field = value
            headers[HEADER_KLAVIYO_ATTEMPT] = "$value/${Registry.config.networkMaxAttempts}"
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
    override val headers: MutableMap<String, String> = mutableMapOf(
        HEADER_CONTENT to TYPE_JSON,
        HEADER_ACCEPT to TYPE_JSON,
        HEADER_REVISION to Registry.config.apiRevision,
        HEADER_USER_AGENT to DeviceProperties.userAgent,
        HEADER_KLAVIYO_MOBILE to "1",
        HEADER_KLAVIYO_ATTEMPT to "$attempts/${Registry.config.networkMaxAttempts}"
    )

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
     * Expected status code from the API backend
     * This varies by version/endpoint so we can override the code by subclass
     */
    protected open val successCodes = HTTP_OK until HTTP_MULT_CHOICE

    /**
     * HTTP Status code from last send attempt
     * null if unsent
     */
    override var responseCode: Int? = null
        protected set

    /**
     * Response headers from Klaviyo
     */
    override var responseHeaders: Map<String, List<String>> = emptyMap()
        protected set

    /**
     * Body of response content from last send attempt
     */
    override var responseBody: String? = null
        protected set

    /**
     * Parsing the error response or creating an empty one if there is none
     */
    override val errorBody: KlaviyoErrorResponse
        by lazy {
            responseBody?.let { body ->
                val responseJson = try {
                    JSONObject(body)
                } catch (e: JSONException) {
                    Registry.log.wtf("Malformed error response body from backend", e)
                    JSONObject()
                }
                KlaviyoErrorResponseDecoder.fromJson(responseJson)
            } ?: KlaviyoErrorResponse(listOf())
        }

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
        .accumulate(HEADERS_JSON_KEY, JSONObject(headers as Map<String, String>))
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
            Registry.log.verbose("Send prevented while network unavailable")
            return status
        }

        status = Status.Inflight
        attempts++

        return try {
            val connection = buildUrlConnection()

            try {
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
        responseHeaders = connection.headerFields

        status = when (responseCode) {
            in successCodes -> Status.Complete
            HTTP_RETRY, HTTP_UNAVAILABLE -> {
                if (attempts < Registry.config.networkMaxAttempts) {
                    Status.PendingRetry
                } else {
                    Status.Failed
                }
            }
            // TODO - Special handling of unauthorized i.e. 401 and 403?
            // TODO - Special handling of server error 500?
            else -> Status.Failed
        }

        val stream = when (status) {
            Status.Complete -> connection.inputStream
            else -> connection.errorStream
        }

        responseBody = BufferedReader(InputStreamReader(stream)).use { it.readText() }

        return status
    }

    /**
     * Compute a retry interval based on state of the request
     *
     * If present, obey the Retry-After response header, plus some jitter.
     * Absent the header, use an exponential backoff algorithm, with a
     * floor set by current network connection, and ceiling set by the config.
     */
    fun computeRetryInterval(): Long {
        val jitterSeconds = Registry.config.networkJitterRange.random()

        try {
            val retryAfter = this.responseHeaders[HEADER_RETRY_AFTER]?.getOrNull(0)

            if (retryAfter?.isNotEmpty() == true) {
                return (retryAfter.toInt() + jitterSeconds).times(1_000L)
            }
        } catch (e: NumberFormatException) {
            Registry.log.warning("Invalid Retry-After header value", e)
        }

        val networkType = Registry.networkMonitor.getNetworkType().position
        val minRetryInterval = Registry.config.networkFlushIntervals[networkType]
        val exponentialBackoff = (2.0.pow(attempts).toLong() + jitterSeconds).times(1_000L)
        val maxRetryInterval = Registry.config.networkMaxRetryInterval

        return min(
            max(minRetryInterval, exponentialBackoff),
            maxRetryInterval
        )
    }

    /**
     * Clear a mutable map and add new key value pairs
     * Utility to replace all headers
     */
    fun <K, V> MutableMap<K, V>.replaceAllWith(newValues: Map<K, V>) = apply {
        clear()
        this += newValues
    }
}

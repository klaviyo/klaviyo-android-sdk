package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.Registry
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_MULT_CHOICE
import java.net.HttpURLConnection.HTTP_OK
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/**
 * Abstract class for handling general networking logic
 *
 * @property urlPath URL this request will be connecting to as string
 * @property method The [RequestMethod] will determine the type of request being made
 */
internal open class KlaviyoApiRequest(
    val urlPath: String,
    val method: RequestMethod,
    val time: String = Registry.clock.currentTimeAsString(),
    val uuid: String = UUID.randomUUID().toString()
) {
    open var headers: Map<String, String> = emptyMap()
    open var query: Map<String, String> = emptyMap()
    open var body: JSONObject? = null

    /**
     * Creates a representation of this [KlaviyoApiRequest] in JSON
     *
     * Note that subclass information is lost in the process.
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
        const val HEADER_CONTENT = "Content-Type"
        const val HEADER_ACCEPT = "Accept"
        const val HEADER_REVISION = "Revision"

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
         */
        fun fromJson(json: JSONObject): KlaviyoApiRequest {
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
    }

    /**
     * Compiles the base url, path and query data into a [URL] object
     */
    val url: URL
        get() {
            val baseUrl = Registry.config.baseUrl
            val queryMap = query.map { (key, value) -> "$key=$value" }
            val queryString = queryMap.joinToString(separator = "&")

            return if (queryString.isEmpty())
                URL("$baseUrl/$urlPath")
            else
                URL("$baseUrl/$urlPath?$queryString")
        }

    /**
     * Builds and sends a network request to Klaviyo and then parses and handles the response
     *
     * @returns The string value of the response body, if one was returned
     */
    fun send(): String? {
        if (!Registry.networkMonitor.isNetworkConnected()) {
            return null
        }

        val connection = buildUrlConnection()

        return try {
            connection.connect()
            return readResponse(connection)
        } catch (ex: IOException) {
            // TODO Logging
            return null
        } finally {
            connection.disconnect()
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

        val bodyString = body?.toString() ?: return connection

        connection.doOutput = true
        HttpUtil.writeToConnection(bodyString, connection)

        return connection
    }

    /**
     * Reads the response body from the open [HttpURLConnection]
     * If the request was successful, extracts the response body
     * If the request was unsuccessful, extracts the error response body
     *
     * @return The string extracted from the response body
     */
    private fun readResponse(connection: HttpURLConnection): String {
        val statusCode = connection.responseCode
        val stream = if (statusCode in HTTP_OK until HTTP_MULT_CHOICE) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val reader = BufferedReader(InputStreamReader(stream))

        return reader.use { it.readText() }
    }
}

/**
 * Utility for opening [URL] connection as HttpURLConnection
 * This method makes it easier to decouple the particulars of HttpUrlConnection
 * from ApiRequest implementation
 */
internal object HttpUtil {
    /**
     * @param url
     * @return
     * @throws IOException
     */
    fun openConnection(url: URL): HttpURLConnection = url.openHttpConnection()

    /**
     * @param body
     * @param connection
     */
    fun writeToConnection(body: String, connection: HttpURLConnection) {
        val writer = connection.outputStream.bufferedWriter()
        writer.use { out -> out.write(body) }
    }

    /**
     * @return
     * @throws IOException
     */
    private fun URL.openHttpConnection(): HttpURLConnection {
        if (this.protocol == "https") {
            return openConnection() as HttpsURLConnection
        }

        if (this.protocol == "http") {
            return openConnection() as HttpURLConnection
        }

        throw IOException("Invalid URL protocol")
    }
}

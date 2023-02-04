package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.Klaviyo
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_MULT_CHOICE
import java.net.HttpURLConnection.HTTP_OK
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Abstract class for handling general networking logic
 *
 * @property urlPath URL this request will be connecting to as string
 * @property method The [RequestMethod] will determine the type of request being made
 */
internal open class KlaviyoApiRequest(
    val urlPath: String,
    val method: RequestMethod
) {
    open var headers: Map<String, String> = emptyMap()
    open var query: Map<String, String> = emptyMap()
    open var body: String? = null

    /**
     * Compiles the base url, path and query data into a [URL] object
     */
    val url: URL
        get() {
            val baseUrl = Klaviyo.Registry.config.baseUrl
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
        if (!Klaviyo.Registry.networkMonitor.isNetworkConnected()) {
            return null
        }

        val connection = buildUrlConnection()

        return try {
            connection.connect()
            return readResponse(connection)
        } catch (ex: IOException) {
            // TODO Logger
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
        connection.readTimeout = Klaviyo.Registry.config.networkTimeout
        connection.connectTimeout = Klaviyo.Registry.config.networkTimeout

        if (!body.isNullOrEmpty() && method == RequestMethod.POST) {
            connection.doOutput = true
            HttpUtil.writeToConnection(body!!, connection)
        }

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

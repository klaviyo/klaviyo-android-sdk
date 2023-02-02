package com.klaviyo.coresdk.networking.requests

import android.webkit.URLUtil
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.networking.RequestMethod
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Abstract class for handling general networking logic
 *
 * @property urlString the URL this request will be connecting to
 * @property requestMethod The [RequestMethod] will determine the type of request being made
 * @property queryData The query information that will be encoded and attached to the URL
 * @property payload The body of the request
 */
internal abstract class NetworkRequest {
    internal abstract val urlString: String
    internal abstract val requestMethod: RequestMethod
    internal open var queryData: Map<String, String> = emptyMap()
    internal open val payload: String? = null

    /**
     * Checks if the device currently has internet access
     *
     * @return Boolean representing whether the internet is currently available or not
     */
    internal fun isInternetConnected(): Boolean =
        Klaviyo.Registry.networkMonitor.isNetworkConnected()

    /**
     * Builds out a full [URL] object after attaching any query data to the url string
     */
    internal fun buildURL(): URL {
        val queryDataString = queryData.map { (key, value) ->
            "$key=$value"
        }.joinToString(separator = "&", prefix = "?")
        return URL("$urlString$queryDataString")
    }

    /**
     * Opens a connection against the given [URL]
     * Connection type is either [HttpURLConnection] or [HttpsURLConnection] depending on the [URL] protocol
     */
    internal fun buildConnection(url: URL): HttpURLConnection {
        return if (URLUtil.isHttpsUrl(url.toString())) {
            url.openConnection() as HttpsURLConnection
        } else {
            url.openConnection() as HttpURLConnection
        }
    }

    /**
     * Setup headers to be included in the request
     */
    internal open fun appendHeaders(connection: HttpURLConnection) {}

    /**
     * Builds and sends a network request to Klaviyo and then parses and handles the response
     *
     * @returns The string value of the response body, if one was returned
     */
    internal open fun sendNetworkRequest(): String? {
        if (!Klaviyo.Registry.networkMonitor.isNetworkConnected()) {
            return null
        }

        val url = buildURL()
        val connection = buildConnection(url)

        connection.readTimeout = Klaviyo.Registry.config.networkTimeout
        connection.connectTimeout = Klaviyo.Registry.config.networkTimeout
        connection.requestMethod = requestMethod.name
        appendHeaders(connection)

        if (connection.requestMethod == RequestMethod.POST.name) {
            connection.doOutput = true

            if (!payload.isNullOrEmpty()) {
                connection.outputStream.bufferedWriter().use { out ->
                    out.write(payload)
                }
            }
        }
        return try {
            connection.connect()
            readResponse(connection)
        } catch (ex: IOException) {
            null
        }
    }

    /**
     * Reads the response body from the open [HttpURLConnection]
     * If the request was successful, extracts the response body
     * If the request was unsuccessful, extracts the error response body
     *
     * @return The string extracted from the response body
     */
    internal fun readResponse(connection: HttpURLConnection): String {
        val response: String

        try {
            val statusCode = connection.responseCode
            response = if (statusCode in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE) {
                readFromStream(connection.inputStream)
            } else {
                readFromStream(connection.errorStream)
            }
        } finally {
            connection.disconnect()
        }
        return response
    }

    /**
     * Reads information from the given [InputStream]
     *
     * @return All string information read by the [InputStream] returned at once since we don't expect large responses
     */
    private fun readFromStream(inputStream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        return reader.use {
            it.readText()
        }
    }
}

package com.klaviyo.coresdk.networking.requests

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Base64
import android.webkit.URLUtil
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.RequestMethod
import java.io.*
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
    internal abstract var urlString: String
    internal abstract var requestMethod: RequestMethod
    internal abstract var queryData: String?
    internal abstract var payload: String?

    /**
     * Checks if the device currently has internet access
     *
     * @param context The context needed to make calls to the Android device and check network status
     *
     * @return Boolean representing whether the internet is currently available or not
     */
    internal fun isInternetConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // TODO: We may want to replace this later. Deprecated as of Android API 29.
        //  But the alternative solution is an asynchronous task that requires the user to register
        //  a network callback listener, which isn't ideal just for a network connectivity check in an SDK
        val networkInfo = connectivityManager.activeNetworkInfo ?: return false
        var usingInternet = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            usingInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        return usingInternet && networkInfo.isConnectedOrConnecting
    }

    /**
     * Builds out a full [URL] object after attaching any query data to the url string
     */
    internal fun buildURL(): URL {
        if (!queryData.isNullOrEmpty()) {
            val query = "data=${encodeToBase64(queryData!!)}"
            urlString += "?$query"
        }

        return URL(urlString)
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
     * Encodes the given string into a non-wrapping base64 string
     * Needed to parse the query data into encoded information
     *
     * @param data the string we want to encode
     */
    internal fun encodeToBase64(data: String): String {
        val dataBytes = data.toByteArray()
        return Base64.encodeToString(dataBytes, Base64.NO_WRAP)
    }

    /**
     * Builds and sends a network request to Klaviyo and then parses and handles the response
     *
     * @returns The string value of the response body, if one was returned
     */
    internal open fun sendNetworkRequest(): String? {
        if (!isInternetConnected(KlaviyoConfig.applicationContext)) {
            return null
        }

        val url = buildURL()
        val connection = buildConnection(url)

        connection.readTimeout = KlaviyoConfig.networkTimeout
        connection.connectTimeout = KlaviyoConfig.networkTimeout
        connection.requestMethod = requestMethod.name

        if (connection.requestMethod == RequestMethod.POST.name) {
            connection.doOutput = true

            if(!payload.isNullOrEmpty()) {
                val outputStream = connection.outputStream
                val writer = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))
                writer.use {
                    it.write(payload!!)
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
            response = if (statusCode == HttpURLConnection.HTTP_OK) {
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
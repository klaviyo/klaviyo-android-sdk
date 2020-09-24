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
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection


internal abstract class NetworkRequest {
    internal abstract var urlString: String
    internal abstract var requestMethod: RequestMethod
    internal abstract var queryData: String?
    internal abstract var payload: String?

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

    internal fun buildURL(): URL {
        if (!queryData.isNullOrEmpty()) {
            val query = "data=${encodeToBase64(queryData!!)}"
            urlString += "?$query"
        }

        return URL(urlString)
    }

    internal fun buildConnection(url: URL): HttpURLConnection {
        return if (URLUtil.isHttpsUrl(url.toString())) {
            url.openConnection() as HttpsURLConnection
        } else {
            url.openConnection() as HttpURLConnection
        }
    }

    internal fun encodeToBase64(data: String): String {
        val dataBytes = data.toByteArray()
        return Base64.encodeToString(dataBytes, Base64.NO_WRAP)
    }

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

    private fun readFromStream(inputStream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        return reader.use {
            it.readText()
        }
    }
}
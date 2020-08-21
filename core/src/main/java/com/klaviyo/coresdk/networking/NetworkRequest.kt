package com.klaviyo.coresdk.networking

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Base64
import com.klaviyo.coresdk.KlaviyoConfig
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection


abstract class NetworkRequest {
    internal abstract var url: URL
    internal abstract var requestMethod: RequestMethod
    internal abstract var headerData: String?
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

    internal fun encodeToBase64(data: String): String {
        val dataBytes = data.toByteArray()
        return Base64.encodeToString(dataBytes, Base64.NO_WRAP)
    }

    internal open fun sendNetworkRequest(): String? {
        if (!isInternetConnected(KlaviyoConfig.applicationContext)) {
            return null
        }

        val connection: HttpsURLConnection = url.openConnection() as HttpsURLConnection

        connection.readTimeout = KlaviyoConfig.networkTimeout
        connection.connectTimeout = KlaviyoConfig.networkTimeout
        connection.requestMethod = requestMethod.name
        connection.setRequestProperty("Content-Type", "application/json; utf-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.doOutput = true
        connection.setRequestProperty("User-agent", System.getProperty("http.agent"))

        if (!headerData.isNullOrEmpty()) {
            connection.setRequestProperty("data", encodeToBase64(headerData!!))
        }

        if (connection.requestMethod == RequestMethod.POST.name && !payload.isNullOrEmpty()) {
            val outputStream = connection.outputStream
            val writer = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))
            writer.use {
                it.write(payload)
            }
        }

        connection.connect()

        return readResponse(connection)
    }

    internal fun readResponse(connection: HttpsURLConnection): String {
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
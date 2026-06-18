package com.klaviyo.analytics.networking.requests

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

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

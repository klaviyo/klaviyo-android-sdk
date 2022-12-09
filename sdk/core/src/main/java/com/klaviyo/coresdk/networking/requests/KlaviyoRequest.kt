package com.klaviyo.coresdk.networking.requests

import android.util.Base64
import com.klaviyo.coresdk.BuildConfig
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.NetworkBatcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

/**
 * Abstract class which defines much of the logic common to requests that will be reaching Klaviyo
 *
 * @property urlString the URL this request will be connecting to
 * @property queryData Query information that will be encoded and attached to the URL
 * @property payload Payload information that will be attached to the request as the body
 */
internal abstract class KlaviyoRequest : NetworkRequest() {
    companion object {
        internal const val BASE_URL = BuildConfig.KLAVIYO_SERVER_URL
    }

    /**
     * A timestamp that can be used to track when this request was created
     */
    internal val timestamp: Long = KlaviyoConfig.clock.currentTimeMillis() / 1000

    internal fun getTimeString(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(timestamp * 1000))
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

    // TODO: Potentially remove this later
    //  This is a function for testing individual requests made instantly
    /**
     * Instantly sends a network request to Klaviyo
     *
     * @return A boolean representing if the request was made successfully or not
     */
    internal fun process(): Boolean {
        val response = sendNetworkRequest()
        return response == "1"
    }

    /**
     * Adds this request object to the network batcher
     */
    internal fun batch() {
        NetworkBatcher.batchRequests(this)
    }
}

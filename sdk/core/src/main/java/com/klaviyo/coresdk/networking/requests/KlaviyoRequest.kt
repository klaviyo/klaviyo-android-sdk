package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.BuildConfig
import com.klaviyo.coresdk.networking.NetworkBatcher

/**
 * Abstract class which defines much of the logic common to requests that will be reaching Klaviyo
 *
 * @property queryData Query information that will be encoded and attached to the URL
 * @property payload Payload information that will be attached to the request as the body
 */
internal abstract class KlaviyoRequest : NetworkRequest() {
    companion object {
        internal const val BASE_URL = BuildConfig.KLAVIYO_SERVER_URL
    }

    override var queryData: String? = null
    override var payload: String? = null

    internal abstract fun buildKlaviyoJsonQuery(): String

    /**
     * Builds out our query data and sends the request to Klaviyo
     */
    override fun sendNetworkRequest(): String? {
        queryData = buildKlaviyoJsonQuery()
        return super.sendNetworkRequest()
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

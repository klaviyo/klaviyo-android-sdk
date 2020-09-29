package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.BuildConfig
import com.klaviyo.coresdk.ConfigFileUtils
import com.klaviyo.coresdk.networking.NetworkBatcher

/**
 * Abstract class which defines much of the logic common to requests that will be reaching Klaviyo
 *
 * @property queryData Query information that will be encoded and attached to the URL
 * @property payload Payload information that will be attached to the request as the body
 */
internal abstract class KlaviyoRequest: NetworkRequest() {
    companion object {
        internal const val BASE_URL = BuildConfig.KLAVIYO_SERVER_URL

        internal const val ANON_KEY = "\$anonymous"
    }

    override var queryData: String? = null
    override var payload: String? = null

    /**
     * Adds an anonymous ID to the given property map.
     * Anonymous IDs are identifiers unique to devices that will be used to identify customers
     * if other customer identifiers have not been added to the request
     *
     * @param map The property map that we are appending this anonymous ID to
     */
    internal fun addAnonymousIdToProps(map: MutableMap<String, String>) {
        map[ANON_KEY] = "Android:${ConfigFileUtils.readOrCreateUUID()}"
    }

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

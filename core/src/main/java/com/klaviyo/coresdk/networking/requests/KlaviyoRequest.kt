package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.networking.NetworkBatcher

internal abstract class KlaviyoRequest: NetworkRequest() {
    companion object {
        internal const val BASE_URL = "https://a.klaviyo.com"

        internal const val ANON_KEY = "\$anonymous"
    }

    override var queryData: String? = null
    override var payload: String? = null

    internal abstract fun addAnonymousIdToProps()

    internal abstract fun buildKlaviyoJsonQuery(): String

    override fun sendNetworkRequest(): String? {
        addAnonymousIdToProps()
        queryData = buildKlaviyoJsonQuery()
        return super.sendNetworkRequest()
    }

    // TODO: Potentially remove this later
    //  This is a function for testing individual requests made instantly
    internal fun process(): Boolean {
        val response = sendNetworkRequest()
        return response == "1"
    }

    internal fun batch() {
        NetworkBatcher.batchRequests(this)
    }
}

package com.klaviyo.coresdk.networking

abstract class KlaviyoRequest: NetworkRequest() {
    companion object {
        internal const val BASE_URL = "https://a.klaviyo.com"
    }

    override var queryData: String? = null
    override var payload: String? = null

    internal abstract fun buildKlaviyoJsonQuery(): String

    override fun sendNetworkRequest(): String? {
        queryData = buildKlaviyoJsonQuery()
        return super.sendNetworkRequest()
    }

    // TODO: Potentially remove this later
    //  This is a function for testing individual requests made instantly
    fun process(): Boolean {
        val response = sendNetworkRequest()
        return response == "1"
    }
}

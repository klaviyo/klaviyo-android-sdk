package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.BuildConfig
import com.klaviyo.coresdk.ConfigFileUtils
import com.klaviyo.coresdk.networking.NetworkBatcher

internal abstract class KlaviyoRequest: NetworkRequest() {
    companion object {
        internal const val BASE_URL = BuildConfig.KLAVIYO_SERVER_URL

        internal const val ANON_KEY = "\$anonymous"
    }

    override var queryData: String? = null
    override var payload: String? = null

    internal fun addAnonymousIdToProps(map: MutableMap<String, String>) {
        map[ANON_KEY] = "Android:${ConfigFileUtils.readOrCreateUUID()}"
    }

    internal abstract fun buildKlaviyoJsonQuery(): String

    override fun sendNetworkRequest(): String? {
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

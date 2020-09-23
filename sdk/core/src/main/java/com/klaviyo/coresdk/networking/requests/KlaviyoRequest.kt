package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.utils.ConfigFileUtils
import com.klaviyo.coresdk.networking.NetworkBatcher
import com.klaviyo.coresdk.utils.ConfigKeys

internal abstract class KlaviyoRequest: NetworkRequest() {
    companion object {
        internal const val BASE_URL = "https://a.klaviyo.com"

        internal const val ANON_KEY = "\$anonymous"
        internal const val PUSH_KEY = "\$android_tokens"
    }

    override var queryData: String? = null
    override var payload: String? = null

    internal fun addAnonymousIdToProps(map: MutableMap<String, String>) {
        map[ANON_KEY] = "Android:${ConfigFileUtils.readOrCreateUUID()}"
    }

    internal fun addPushTokenToProps(map: MutableMap<String, String>) {
        val token = ConfigFileUtils.readValue(ConfigKeys.PUSH_TOKEN_KEY)

        if (token.isNotEmpty()) {
            map[PUSH_KEY] = token
        }
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

package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.BuildConfig
import com.klaviyo.coresdk.networking.NetworkBatcher
import com.klaviyo.coresdk.networking.UserInfo
import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils

internal abstract class KlaviyoRequest: NetworkRequest() {
    companion object {
        internal const val BASE_URL = BuildConfig.KLAVIYO_SERVER_URL

        internal const val ANON_KEY = "\$anonymous"
        internal const val EMAIL_KEY = "\$email"
    }

    override var queryData: String? = null
    override var payload: String? = null

    internal fun addAnonymousIdToProps(map: MutableMap<String, Any>) {
        map[ANON_KEY] = "Android:${KlaviyoPreferenceUtils.readOrGenerateUUID()}"
    }

    internal fun addEmailToProps(map: MutableMap<String, Any>) {
        if (map[EMAIL_KEY] == null && UserInfo.hasEmail()) {
            map[EMAIL_KEY] = UserInfo.email
        } else {
            UserInfo.email = map[EMAIL_KEY].toString()
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

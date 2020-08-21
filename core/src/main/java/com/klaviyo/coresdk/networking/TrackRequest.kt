package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.KlaviyoConfig
import org.json.JSONObject

class TrackRequest (
        private var event: String,
        private var customerProperties: Map<String, String>,
        private var properties: Map<String, String>? = null
): KlaviyoRequest() {
    companion object {
        internal const val TRACK_ENDPOINT = "api/track"
    }

    private var timestamp: Long? = null

    override var urlString = "$BASE_URL/$TRACK_ENDPOINT"
    override var requestMethod = RequestMethod.GET


    override fun buildKlaviyoJsonQuery(): String {
        val json = JSONObject()

        json.put("token", KlaviyoConfig.apiKey)
        json.put("event", event)

        val customerPropsJson = JSONObject()
        customerProperties.forEach {
            customerPropsJson.putOpt(it.key, it.value)
        }
        if (customerPropsJson.length() > 0) {
            json.putOpt("customer_properties", customerPropsJson)
        }

        val propsJson = JSONObject()
        properties?.forEach {
            propsJson.putOpt(it.key, it.value)
        }
        if (propsJson.length() > 0) {
            json.putOpt("properties", propsJson)
        }

        json.putOpt("time", timestamp)

        return json.toString()
    }

    fun generateUnixTimestamp() {
        timestamp = System.currentTimeMillis() / 1000L
    }
}

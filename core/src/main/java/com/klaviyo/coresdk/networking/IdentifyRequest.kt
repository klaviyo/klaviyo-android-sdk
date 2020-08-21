package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.KlaviyoConfig
import org.json.JSONObject
import java.net.URL

class IdentifyRequest (
        private var event: String,
        private var properties: Map<String, String>
): KlaviyoRequest() {
    companion object {
        internal const val IDENTIFY_ENDPOINT = "api/identify"
    }

    override var url = URL("$BASE_URL/$IDENTIFY_ENDPOINT")
    override var requestMethod = RequestMethod.GET

    override fun buildKlaviyoJsonHeader(): String {
        val json = JSONObject()

        json.put("token", KlaviyoConfig.apiKey)
        json.put("event", event)

        val propsJson = JSONObject()
        properties.forEach {
            propsJson.putOpt(it.key, it.value)
        }
        if (propsJson.length() > 0) {
            json.putOpt("properties", propsJson)
        }

        return json.toString()
    }
}

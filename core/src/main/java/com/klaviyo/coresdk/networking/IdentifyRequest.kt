package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.KlaviyoConfig
import org.json.JSONObject

class IdentifyRequest (
        private var properties: Map<String, String>
): KlaviyoRequest() {
    companion object {
        internal const val IDENTIFY_ENDPOINT = "api/identify"
    }

    override var urlString = "$BASE_URL/$IDENTIFY_ENDPOINT"
    override var requestMethod = RequestMethod.GET

    /**
     * Example request:
     * {
        "token" : "apikey",
        "properties" : {
        "$email" : "sdktest@test.com",
        "$value" : 11250000,
        "From" : "France",
        "SquareMiles" : 828000
        },
        "time" : 1598038143
        }
     */
    override fun buildKlaviyoJsonQuery(): String {
        return JSONObject(
            mapOf(
                "token" to KlaviyoConfig.apiKey,
                "properties" to JSONObject(properties)
            )
        ).toString()
    }
}

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


    /**
     * Example request:
     * {
        "token" : "apikey",
        "properties" : {
        "$email" : "myemail@domain.com",
        "$first_name" : "Me",
        "$last_name" : "You",
        "Plan" : "Premium",
        "SignUpDate" : "2016-05-01 10:10:00"
        }
        }
     */
    override fun buildKlaviyoJsonQuery(): String {
        val json = JSONObject(
            mapOf(
                "token" to KlaviyoConfig.apiKey,
                "event" to event,
                "customer_properties" to JSONObject(customerProperties),
                "properties" to properties?.let { JSONObject(properties) },
                "time" to timestamp?.let { it }
            ).filterValues { it != null }
        )

        return json.toString()
    }

    fun generateUnixTimestamp() {
        timestamp = System.currentTimeMillis() / 1000L
    }
}

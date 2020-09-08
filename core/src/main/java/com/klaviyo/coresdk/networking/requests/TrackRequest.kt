package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.ConfigFileUtils
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.RequestMethod
import org.json.JSONObject

internal class TrackRequest (
        private var event: String,
        private var customerProperties: MutableMap<String, String>,
        private var properties: Map<String, String>? = null
): KlaviyoRequest() {
    internal companion object {
        const val TRACK_ENDPOINT = "api/track"
    }

    private var timestamp: Long? = null

    override var urlString = "$BASE_URL/$TRACK_ENDPOINT"
    override var requestMethod = RequestMethod.GET

    override fun addAnonymousIdToProps() {
        customerProperties[ANON_KEY] = "Android:${ConfigFileUtils.readOrCreateUUID()}"
    }

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
                "customer_properties" to JSONObject(customerProperties.toMap()),
                "properties" to properties?.let { JSONObject(properties) },
                "time" to timestamp?.let { it }
            ).filterValues { it != null }
        )

        return json.toString()
    }

    internal fun generateUnixTimestamp() {
        timestamp = System.currentTimeMillis() / 1000L
    }
}

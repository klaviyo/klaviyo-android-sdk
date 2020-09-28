package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.RequestMethod
import org.json.JSONObject

/**
 * Defines information unique to building a valid track request for a [KlaviyoEvent]
 *
 * @property customerProperties map of customer information we will be using to identify the user
 * @property properties map of property information we will be attaching to this request
 *
 * @property timestamp The time that this event occurred
 * @property urlString The URL needed to reach the track API in Klaviyo
 * @property requestMethod [RequestMethod] determines the type of request that track requests are made over
 */
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

    /**
     * Builds a JSON payload suitable for a track request and returns it as a String
     * Appends external information to the customer properties map before serializing it to JSON
     *
     * For more information on the structure of Klaviyo requests please reference the API docs:
     * https://www.klaviyo.com/docs
     *
     * @return JSON payload as a string
     */
    override fun buildKlaviyoJsonQuery(): String {
        addAnonymousIdToProps(customerProperties)
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

    /**
     * Generates a Unix timestamp to store  as the [timestamp] on this object
     */
    internal fun generateUnixTimestamp() {
        timestamp = System.currentTimeMillis() / 1000L
    }
}

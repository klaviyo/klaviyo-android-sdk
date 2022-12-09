package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.networking.KlaviyoEvent
import com.klaviyo.coresdk.networking.KlaviyoEventProperties
import com.klaviyo.coresdk.networking.RequestMethod
import java.net.HttpURLConnection
import org.json.JSONObject

/**
 * Defines information unique to building a valid track request for a [KlaviyoEvent]
 *
 * @constructor apiKey - the API key to identify this request
 * @constructor event - the [KlaviyoEvent] to track
 * @constructor customerProperties - map of customer information we will be using to identify the user
 * @constructor properties - map of property information we will be attaching to this request
 *
 * @property timestamp The time that this event occurred
 * @property urlString The URL needed to reach the track API in Klaviyo
 * @property requestMethod [RequestMethod] determines the type of request that track requests are made over
 */
internal class TrackRequest(
    apiKey: String,
    event: KlaviyoEvent,
    customerProperties: KlaviyoCustomerProperties,
    properties: KlaviyoEventProperties? = null
) : KlaviyoRequest() {
    internal companion object {
        const val TRACK_ENDPOINT = "client/events"
    }

    override var urlString = "$BASE_URL/$TRACK_ENDPOINT"
    override var requestMethod = RequestMethod.POST

    override var queryData: Map<String, String> = mapOf(
        "company_id" to apiKey
    )

    override var payload: String? =
        JSONObject(
            mapOf(
                "data" to mapOf(
                    "type" to "event",
                    "attributes" to mapOf(
                        "metric" to mapOf(
                            "name" to event.name,
                        ),
                        "profile" to JSONObject(customerProperties.setAnonymousId().toMap()),
                        "properties" to properties?.let { JSONObject(it.toMap()) },
                        "time" to getTimeString(),
                    ).filterValues { it != null }
                )
            )
        ).toString()

    override fun appendHeaders(connection: HttpURLConnection) {
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Revision", "2022-10-17")
    }
}

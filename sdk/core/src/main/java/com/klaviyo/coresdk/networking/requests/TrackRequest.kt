package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.KlaviyoEventType
import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.networking.RequestMethod
import java.net.HttpURLConnection
import org.json.JSONObject

/**
 * Defines information unique to building a valid track request for a [KlaviyoEventType]
 *
 * @constructor apiKey - the API key to identify this request
 * @constructor eventType - the [KlaviyoEventType] to track
 * @constructor eventAttributes - map of property information we will be attaching to this request
 * @constructor profile - map of profile information we will be using to identify the user
 *
 * @property timestamp The time that this event occurred
 * @property urlString The URL needed to reach the track API in Klaviyo
 * @property requestMethod [RequestMethod] determines the type of request that track requests are made over
 */
internal class TrackRequest(
    apiKey: String,
    eventType: KlaviyoEventType,
    profile: Profile,
    eventAttributes: Event? = null
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
                            "name" to eventType.name,
                        ),
                        "profile" to JSONObject(profile.toMap()),
                        "properties" to eventAttributes?.let { JSONObject(it.toMap()) },
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

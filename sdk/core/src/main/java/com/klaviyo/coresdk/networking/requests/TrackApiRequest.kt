package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.KlaviyoEventType
import com.klaviyo.coresdk.model.Profile
import org.json.JSONObject

/**
 * Defines information unique to building a valid track request for a [KlaviyoEventType]
 *
 * @constructor
 * @param eventType
 * @param profile
 * @param eventAttributes
 */
internal class TrackApiRequest(
    eventType: KlaviyoEventType,
    profile: Profile,
    eventAttributes: Event? = null
) : KlaviyoApiRequest(
    "client/events",
    RequestMethod.POST
) {
    override var headers: Map<String, String> = mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "Revision" to "2022-10-17"
    )

    override var query: Map<String, String> = mapOf(
        "company_id" to Klaviyo.Registry.config.apiKey
    )

    override var body: JSONObject? = JSONObject(
        mapOf(
            "data" to mapOf(
                "type" to "event",
                "attributes" to mapOf(
                    "metric" to mapOf(
                        "name" to eventType.name,
                    ),
                    "profile" to JSONObject(profile.toMap()),
                    "properties" to eventAttributes?.let { JSONObject(it.toMap()) },
                    "time" to time
                ).filterValues { it != null }
            )
        )
    )
}
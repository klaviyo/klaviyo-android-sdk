package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventType
import com.klaviyo.analytics.model.Profile
import com.klaviyo.coresdk.Registry
import org.json.JSONObject

/**
 * Defines information unique to building a valid track request for a [EventType]
 *
 * @constructor
 * @param event
 * @param profile
 */
internal class TrackApiRequest(
    event: Event,
    profile: Profile,
) : KlaviyoApiRequest(
    "client/events",
    RequestMethod.POST
) {
    override var headers: Map<String, String> = mapOf(
        HEADER_CONTENT to "application/json",
        HEADER_ACCEPT to "application/json",
        HEADER_REVISION to "2022-10-17"
    )

    override var query: Map<String, String> = mapOf(
        "company_id" to Registry.config.apiKey
    )

    override var body: JSONObject? = JSONObject(
        mapOf(
            "data" to mapOf(
                "type" to "event",
                "attributes" to mapOf(
                    "metric" to mapOf(
                        "name" to event.type.name
                    ),
                    "profile" to profile.toMap(),
                    "properties" to event.toMap().ifEmpty { null },
                    "time" to time
                ).filterValues { it != null }
            )
        )
    )
}

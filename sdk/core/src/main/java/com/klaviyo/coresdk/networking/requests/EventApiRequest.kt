package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.Registry
import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.Profile
import org.json.JSONObject

/**
 * Defines the content of an API request to track an [Event] for a given [Profile]
 *
 * Using V3 API
 *
 * @constructor
 * @param event Event type and attributes to track
 * @param profile Profile the event belongs to
 */
internal class EventApiRequest(
    event: Event,
    profile: Profile,
) : KlaviyoApiRequest(
    PATH,
    RequestMethod.POST
) {

    private companion object {
        const val PATH = "client/events"
        const val METRIC = "metric"
        const val NAME = "name"
        const val VALUE = "value"
        const val TIME = "time"
    }

    override var headers: Map<String, String> = mapOf(
        HEADER_CONTENT to TYPE_JSON,
        HEADER_ACCEPT to TYPE_JSON,
        HEADER_REVISION to V3_REVISION
    )

    override var query: Map<String, String> = mapOf(
        COMPANY_ID to Registry.config.apiKey
    )

    override var body: JSONObject? = formatBody(
        TYPE to EVENT,
        ATTRIBUTES to filteredMapOf(
            PROFILE to profile.getIdentifiers().mapKeys { it.key.specialKey() },
            METRIC to mapOf(NAME to event.type.name),
            VALUE to event.value,
            TIME to time,
            PROPERTIES to event.toMap(),
        )
    )
}

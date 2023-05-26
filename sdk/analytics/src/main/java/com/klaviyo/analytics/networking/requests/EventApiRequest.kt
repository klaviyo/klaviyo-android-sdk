package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.DeviceProperties
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.Profile
import com.klaviyo.core.Registry

/**
 * Defines the content of an API request to track an [Event] for a given [Profile]
 *
 * Using V3 API
 *
 * @constructor
 */
internal class EventApiRequest(
    queuedTime: Long? = null,
    uuid: String? = null
) : KlaviyoApiRequest(PATH, RequestMethod.POST, queuedTime, uuid) {

    private companion object {
        const val PATH = "client/events/"
        const val METRIC = "metric"
        const val NAME = "name"
        const val VALUE = "value"
        const val TIME = "time"
    }

    override var type: String = "Create Event"

    override var headers: Map<String, String> = mapOf(
        HEADER_CONTENT to TYPE_JSON,
        HEADER_ACCEPT to TYPE_JSON,
        HEADER_REVISION to V3_REVISION,
        HEADER_USER_AGENT to DeviceProperties.userAgent
    )

    override var query: Map<String, String> = mapOf(
        COMPANY_ID to Registry.config.apiKey
    )

    override val successCodes: IntRange get() = HTTP_ACCEPTED..HTTP_ACCEPTED

    constructor(event: Event, profile: Profile) : this() {
        body = jsonMapOf(
            DATA to mapOf(
                TYPE to EVENT,
                ATTRIBUTES to filteredMapOf(
                    PROFILE to profile.getIdentifiers().mapKeys { it.key.specialKey() },
                    METRIC to mapOf(NAME to event.type.name),
                    VALUE to event.value,
                    TIME to Registry.clock.isoTime(queuedTime),
                    PROPERTIES to event.toMap(),
                    allowEmptyMaps = true
                )
            )
        )
    }
}

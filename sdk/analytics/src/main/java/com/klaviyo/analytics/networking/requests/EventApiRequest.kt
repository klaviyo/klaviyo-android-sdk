package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.DeviceProperties
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.Profile
import com.klaviyo.core.Registry
import org.json.JSONObject

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

    override var query: Map<String, String> = mapOf(
        COMPANY_ID to Registry.config.apiKey
    )

    override val successCodes: IntRange get() = HTTP_ACCEPTED..HTTP_ACCEPTED

    override var body: JSONObject? = null
        get() {
            // Update body to include Device metadata whenever the body is retrieved (typically during sending) so the latest data is included
            field?.getJSONObject(DATA)?.getJSONObject(ATTRIBUTES)?.getJSONObject(PROPERTIES)?.apply {
                DeviceProperties.buildEventMetaData().forEach { entry ->
                    put(entry.key, entry.value)
                }
            }
            return field
        }

    constructor(event: Event, profile: Profile) : this() {
        body = jsonMapOf(
            DATA to mapOf(
                TYPE to EVENT,
                ATTRIBUTES to filteredMapOf(
                    PROFILE to mapOf(*ProfileApiRequest.formatBody(profile)),
                    METRIC to mapOf(
                        DATA to mapOf(
                            TYPE to METRIC,
                            ATTRIBUTES to mapOf(NAME to event.metric.name)
                        )
                    ),
                    VALUE to event.value,
                    TIME to Registry.clock.isoTime(queuedTime),
                    PROPERTIES to event.toMap(),
                    allowEmptyMaps = true
                )
            )
        )
    }
}

package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.Profile
import com.klaviyo.core.DeviceProperties
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
        const val PATH = "client/events"
        const val METRIC = "metric"
        const val NAME = "name"
        const val VALUE = "value"
        const val TIME = "time"
        const val UNIQUE_ID = "unique_id"
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
        val event = event.copy()

        Registry.log.wtf("uuid is $uuid")

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
                    UNIQUE_ID to event.pop(EventKey.EVENT_ID).let { it as? String ?: uuid },
                    VALUE to event.pop(EventKey.VALUE),
                    TIME to Registry.clock.isoTime(queuedTime),
                    PROPERTIES to event.toMap(),
                    allowEmptyMaps = true
                )
            )
        )
    }
}

internal fun DeviceProperties.buildEventMetaData(): Map<String, String?> = mapOf(
    "Device ID" to deviceId,
    "Device Manufacturer" to manufacturer,
    "Device Model" to model,
    "OS Name" to platform,
    "OS Version" to osVersion,
    "SDK Name" to sdkName,
    "SDK Version" to sdkVersion,
    "App Name" to applicationLabel,
    "App ID" to applicationId,
    "App Version" to appVersion,
    "App Build" to appVersionCode,
    "Push Token" to Klaviyo.getPushToken()
)

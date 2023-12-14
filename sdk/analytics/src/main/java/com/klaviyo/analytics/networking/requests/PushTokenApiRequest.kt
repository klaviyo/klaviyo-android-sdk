package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.DeviceProperties
import com.klaviyo.analytics.model.Profile
import com.klaviyo.core.Registry
import org.json.JSONObject

/**
 * Defines the content of an API request to append a push token to a [Profile]
 *
 * Using legacy V2 API until push tokens are supported by a V3 endpoint
 *
 * @constructor
 */
internal class PushTokenApiRequest(
    queuedTime: Long? = null,
    uuid: String? = null
) : KlaviyoApiRequest(PATH, RequestMethod.POST, queuedTime, uuid) {

    private companion object {
        const val PATH = "client/push-tokens"
        const val METADATA = "device_metadata"
        const val TOKEN = "token"
        const val PLATFORM = "platform"

        const val VENDOR = "vendor"
        const val VENDOR_FCM = "FCM"

        const val ENABLEMENT_STATUS = "enablement_status"
        const val NOTIFICATIONS_ENABLED = "AUTHORIZED"
        const val NOTIFICATIONS_DISABLED = "UNAUTHORIZED"

        const val BACKGROUND = "background"
        const val BG_AVAILABLE = "AVAILABLE"
        const val BG_UNAVAILABLE = "DENIED"
    }

    override val type: String = "Push Token"

    /**
     * HTTP request headers
     */
    override var headers: Map<String, String> = mapOf(
        HEADER_CONTENT to TYPE_JSON,
        HEADER_ACCEPT to TYPE_JSON,
        HEADER_REVISION to V3_REVISION,
        HEADER_USER_AGENT to DeviceProperties.userAgent
    )

    /**
     * HTTP request query params
     */
    override var query: Map<String, String> = mapOf(
        COMPANY_ID to Registry.config.apiKey
    )

    override val successCodes: IntRange get() = HTTP_ACCEPTED..HTTP_ACCEPTED

    override var body: JSONObject? = null
        get() {
            // Update body to include Device metadata whenever the body is retrieved (typically during sending) so the latest data is included
            field?.getJSONObject(DATA)?.getJSONObject(ATTRIBUTES)?.apply {
                put(
                    ENABLEMENT_STATUS,
                    if (DeviceProperties.notificationPermission) NOTIFICATIONS_ENABLED else NOTIFICATIONS_DISABLED
                )
                put(
                    BACKGROUND,
                    if (DeviceProperties.backgroundData) BG_AVAILABLE else BG_UNAVAILABLE
                )
                put(METADATA, JSONObject(DeviceProperties.buildMetaData()))
            }
            return field
        }

    private lateinit var initialBody: String

    constructor(token: String, profile: Profile) : this() {
        body = jsonMapOf(
            DATA to mapOf(
                TYPE to PUSH_TOKEN,
                ATTRIBUTES to filteredMapOf(
                    PROFILE to mapOf(*ProfileApiRequest.formatBody(profile)),
                    TOKEN to token,
                    PLATFORM to DeviceProperties.platform,
                    VENDOR to VENDOR_FCM
                )
            )
        ).also {
            initialBody = it.toString()
        }
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is PushTokenApiRequest -> initialBody == other.initialBody
            else -> super.equals(other)
        }
    }

    override fun hashCode(): Int {
        return initialBody.hashCode()
    }
}

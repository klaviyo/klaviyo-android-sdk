package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.DeviceProperties
import com.klaviyo.analytics.model.Profile
import com.klaviyo.core.Registry

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
        const val BG_UNAVAILABLE = "UNAVAILABLE"
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

    constructor(token: String, profile: Profile) : this() {
        body = jsonMapOf(
            DATA to mapOf(
                TYPE to PUSH_TOKEN,
                ATTRIBUTES to filteredMapOf(
                    TOKEN to token,
                    PLATFORM to DeviceProperties.platform,
                    VENDOR to VENDOR_FCM,
                    ENABLEMENT_STATUS to if (DeviceProperties.notificationPermission) NOTIFICATIONS_ENABLED else NOTIFICATIONS_DISABLED,
                    BACKGROUND to if (DeviceProperties.backgroundData) BG_AVAILABLE else BG_UNAVAILABLE,
                    METADATA to DeviceProperties.buildMetaData(),
                    PROFILE to mapOf(*ProfileApiRequest.formatBody(profile))
                )
            )
        )
    }
}

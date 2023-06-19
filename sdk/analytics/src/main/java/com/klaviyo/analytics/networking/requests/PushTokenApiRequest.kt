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
    }

    override val type: String = "Push Tokens"

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
                    "token_id" to token,
                    "platform" to DeviceProperties.platform,
                    "vendor" to "FCM",
                    "enablement_status" to "AUTHORIZED",
                    "background" to "AVAILABLE",
                    METADATA to DeviceProperties.buildMetaData(),
                    PROFILE to mapOf(
                        DATA to mapOf(
                            TYPE to PROFILE,
                            ATTRIBUTES to profile.getIdentifiers()
                        )
                    )
                )
            )
        )
    }
}

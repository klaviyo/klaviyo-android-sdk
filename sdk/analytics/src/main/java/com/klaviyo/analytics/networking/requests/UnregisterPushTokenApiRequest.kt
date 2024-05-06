package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.DeviceProperties
import com.klaviyo.analytics.model.Profile

/**
 * Defines the content of an API request to remove a push token from a [Profile]
 *
 * @constructor
 */
internal class UnregisterPushTokenApiRequest(
    queuedTime: Long? = null,
    uuid: String? = null
) : KlaviyoApiRequest(PATH, RequestMethod.POST, queuedTime, uuid) {

    private companion object {
        const val PATH = "client/push-token-unregister"
        const val TOKEN = "token"
        const val PLATFORM = "platform"

        const val VENDOR = "vendor"
        const val VENDOR_FCM = "FCM"
    }

    override val type: String = "Unregister Push Token"

    /**
     * HTTP request query params
//     */
//    override var query: Map<String, String> = mapOf(
//        COMPANY_ID to Registry.config.apiKey
//    )

    override val successCodes: IntRange get() = HTTP_ACCEPTED..HTTP_ACCEPTED

    constructor(token: String, profile: Profile) : this() {
        body = jsonMapOf(
            DATA to mapOf(
                TYPE to UNREGISTER_PUSH_TOKEN,
                ATTRIBUTES to filteredMapOf(
                    PROFILE to mapOf(*ProfileApiRequest.formatBody(profile)),
                    TOKEN to token,
                    PLATFORM to DeviceProperties.platform,
                    VENDOR to VENDOR_FCM
                )
            )
        )
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is UnregisterPushTokenApiRequest -> body.toString() == other.body.toString() && query.toString() == other.query.toString()
            else -> super.equals(other)
        }
    }

    override fun hashCode(): Int {
        return body.toString().hashCode()
    }
}

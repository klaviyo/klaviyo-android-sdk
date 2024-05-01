package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.DeviceProperties
import com.klaviyo.analytics.model.Profile
import com.klaviyo.core.Registry
import org.json.JSONObject

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

    override val type: String = "Unregister Push Token"

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

    // Update body to include Device metadata whenever the body is retrieved (typically during sending) so the latest data is included
    override val requestBody: String?
        get() = body?.apply {
            optJSONObject(DATA)?.optJSONObject(ATTRIBUTES)?.apply {
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
        }?.toString()

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is UnregisterPushTokenApiRequest -> body.toString() == other.body.toString()
            else -> super.equals(other)
        }
    }

    override fun hashCode(): Int {
        return body.toString().hashCode()
    }
}

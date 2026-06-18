package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Profile
import com.klaviyo.core.DeviceProperties
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
                    if (DeviceProperties.notificationPermissionGranted) NOTIFICATIONS_ENABLED else NOTIFICATIONS_DISABLED
                )
                put(
                    BACKGROUND,
                    if (DeviceProperties.backgroundDataEnabled) BG_AVAILABLE else BG_UNAVAILABLE
                )
                put(METADATA, JSONObject(DeviceProperties.buildMetaData()))
            }
        }?.toString()

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is PushTokenApiRequest -> body.toString() == other.body.toString() && query.toString() == other.query.toString()
            else -> super.equals(other)
        }
    }

    override fun hashCode(): Int {
        return body.toString().hashCode()
    }
}

fun DeviceProperties.buildMetaData(): Map<String, String?> = mapOf(
    "device_id" to deviceId,
    "manufacturer" to manufacturer,
    "device_model" to model,
    "os_name" to platform,
    "os_version" to osVersion,
    "klaviyo_sdk" to sdkName,
    "sdk_version" to sdkVersion,
    "app_name" to applicationLabel,
    "app_id" to applicationId,
    "app_version" to appVersion,
    "app_build" to appVersionCode,
    "environment" to environment
)

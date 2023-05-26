package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.DeviceProperties
import com.klaviyo.analytics.model.Profile
import com.klaviyo.core.Registry
import java.io.Serializable
import java.net.HttpURLConnection
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
        const val PATH = "api/identify"
        const val TOKEN = "token"
        const val APPEND = "\$append"
        const val ANDROID_TOKEN = "\$android_tokens"
    }

    override val type: String = "Push Token"

    override var headers: Map<String, String> = mapOf(
        HEADER_CONTENT to TYPE_JSON,
        HEADER_USER_AGENT to DeviceProperties.userAgent
    )

    override val successCodes: IntRange get() = HTTP_OK..HTTP_OK

    override fun parseResponse(connection: HttpURLConnection): Status {
        super.parseResponse(connection)

        // V2 APIs did not properly use status codes.
        if (status == Status.Complete && responseBody == "0") {
            status = Status.Failed
        }

        return status
    }

    constructor(token: String, profile: Profile) : this() {
        // Only send profile's identifiers, plus the push token as an appended property
        val properties: Map<String, Serializable> = profile.getIdentifiers()
            .mapKeys { it.key.specialKey() }
            .plus(APPEND to hashMapOf(ANDROID_TOKEN to token))

        body = JSONObject(
            mapOf(
                TOKEN to Registry.config.apiKey, // API Key, not to be confused with the push token!
                PROPERTIES to JSONObject(properties)
            )
        )
    }
}

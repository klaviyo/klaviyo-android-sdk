package com.klaviyo.analytics.networking.requests

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
 * @param token The push token
 * @param profile Profile identifiers that the token belongs to
 */
internal class PushTokenApiRequest(token: String, profile: Profile) : KlaviyoApiRequest(
    PATH,
    RequestMethod.POST
) {

    private companion object {
        const val PATH = "api/identify"
        const val TOKEN = "token"
        const val APPEND = "\$append"
        const val ANDROID_TOKEN = "\$android_tokens"
    }

    override var headers: Map<String, String> = mapOf(
        HEADER_CONTENT to TYPE_JSON
    )

    /**
     * Only send profile's identifiers, plus the push token as an appended property
     */
    private val properties: Map<String, Serializable> = profile.getIdentifiers()
        .mapKeys { it.key.specialKey() }
        .plus(APPEND to hashMapOf(ANDROID_TOKEN to token))

    override var body: JSONObject? = JSONObject(
        mapOf(
            TOKEN to Registry.config.apiKey, // API Key, not to be confused with the push token!
            PROPERTIES to JSONObject(properties)
        )
    )

    // V2 API had this funky data format mixing json and form fields
    override fun formatBody(): String = body.toString()

    override fun parseResponse(connection: HttpURLConnection): Status {
        super.parseResponse(connection)

        // V2 APIs did not properly use status codes.
        if (status == Status.Complete && response == "0") {
            status = Status.Failed
        }

        return status
    }
}

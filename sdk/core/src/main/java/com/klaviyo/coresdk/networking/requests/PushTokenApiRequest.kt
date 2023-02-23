package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.Registry
import com.klaviyo.coresdk.model.Profile
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
        const val PATH = "api/identify/"
        const val TOKEN = "token"
        const val APPEND = "\$append"
        const val ANDROID_TOKEN = "\$android_tokens"
    }

    /**
     * Only send profile's identifiers, plus the push token as an appended property
     */
    private val properties = profile.getIdentifiers().mapKeys { it.key.specialKey() }
        .plus(APPEND to hashMapOf(ANDROID_TOKEN to token))

    override var body: JSONObject? = formatBody(
        TOKEN to Registry.config.apiKey, // API Key, not to be confused with the push token!
        PROPERTIES to JSONObject(properties)
    )
}

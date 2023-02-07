package com.klaviyo.coresdk.networking.requests

import android.util.Base64
import com.klaviyo.coresdk.Registry
import com.klaviyo.coresdk.model.Profile
import org.json.JSONObject

/**
 * Defines information unique to building a valid identify request
 *
 * @param profile Profile attributes to send
 */
internal class IdentifyApiRequest(profile: Profile) : KlaviyoApiRequest(
    "api/identify",
    RequestMethod.GET
) {

    override var query: Map<String, String> = profile.let { properties ->
        val data = JSONObject(
            mapOf(
                "token" to Registry.config.apiKey,
                "properties" to JSONObject(properties.toMap())
            )
        ).toString()

        mapOf(
            "data" to Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP)
        )
    }
}

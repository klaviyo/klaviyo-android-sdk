package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.networking.RequestMethod
import org.json.JSONObject

/**
 * Defines information unique to building a valid identify request
 *
 * @constructor apiKey - the API key to identify this request
 * @constructor properties - map of property information we will be attaching to this request
 *
 * @property urlString the URL needed to reach the identify API in Klaviyo
 * @property requestMethod [RequestMethod] determines the type of request that identify requests are made over
 */
internal class IdentifyRequest(
    apiKey: String,
    properties: KlaviyoCustomerProperties
) : KlaviyoRequest() {
    internal companion object {
        const val IDENTIFY_ENDPOINT = "api/identify"
    }

    override var urlString = "$BASE_URL/$IDENTIFY_ENDPOINT"
    override var requestMethod = RequestMethod.GET

    override var queryData: Map<String, String> = JSONObject(properties.setAnonymousId().toMap()).let { properties ->
        val data = JSONObject(
            mapOf(
                "token" to apiKey,
                "properties" to properties
            )
        ).toString()
        mapOf(
            "data" to encodeToBase64(data)
        )
    }
}

package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.networking.RequestMethod
import org.json.JSONObject

/**
 * Defines information unique to building a valid identify request
 *
 * @property properties map of property information we will be attaching to this request
 *
 * @property urlString the URL needed to reach the identify API in Klaviyo
 * @property requestMethod [RequestMethod] determines the type of request that identify requests are made over
 */
internal class IdentifyRequest(
    private var properties: KlaviyoCustomerProperties
) : KlaviyoRequest() {
    internal companion object {
        const val IDENTIFY_ENDPOINT = "api/identify"
    }

    override var urlString = "$BASE_URL/$IDENTIFY_ENDPOINT"
    override var requestMethod = RequestMethod.GET

    private val finalProperties: JSONObject = JSONObject(properties.addAnonymousId().toMap())

    /**
     * Builds a JSON payload suitable for an identify request and returns it as a String
     * Appends external information to the properties map before serializing it to JSON
     *
     * For more information on the structure of Klaviyo requests please reference the API docs:
     * https://www.klaviyo.com/docs
     *
     * @return JSON payload as a string
     */
    override fun buildKlaviyoJsonQuery(): String {
        return JSONObject(
            mapOf(
                "token" to KlaviyoConfig.apiKey,
                "properties" to finalProperties
            )
        ).toString()
    }
}

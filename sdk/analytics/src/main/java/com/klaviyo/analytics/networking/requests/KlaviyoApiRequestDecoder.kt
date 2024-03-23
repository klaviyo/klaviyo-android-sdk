package com.klaviyo.analytics.networking.requests

import org.json.JSONException
import org.json.JSONObject

internal object KlaviyoApiRequestDecoder {

    /**
     * Construct a request from a JSON object
     *
     * @return Request object of original subclass type
     * @throws JSONException If required fields are missing or improperly formatted
     */
    internal fun fromJson(json: JSONObject): KlaviyoApiRequest {
        val urlPath = json.getString(KlaviyoApiRequest.PATH_JSON_KEY)
        val method = when (json.getString(KlaviyoApiRequest.METHOD_JSON_KEY)) {
            RequestMethod.POST.name -> RequestMethod.POST
            else -> RequestMethod.GET
        }
        val time = json.getLong(KlaviyoApiRequest.TIME_JSON_KEY)
        val uuid = json.getString(KlaviyoApiRequest.UUID_JSON_KEY)

        return when (json.optString(KlaviyoApiRequest.TYPE_JSON_KEY)) {
            ProfileApiRequest::class.simpleName -> ProfileApiRequest(time, uuid)
            EventApiRequest::class.simpleName -> EventApiRequest(time, uuid)
            PushTokenApiRequest::class.simpleName -> PushTokenApiRequest(time, uuid)
            else -> KlaviyoApiRequest(urlPath, method, time, uuid)
        }.apply {
            headers = json.getJSONObject(KlaviyoApiRequest.HEADERS_JSON_KEY).let {
                it.keys().asSequence().associateWith { k -> it.getString(k) }.toMutableMap()
            }
            query = json.getJSONObject(KlaviyoApiRequest.QUERY_JSON_KEY).let {
                it.keys().asSequence().associateWith { k -> it.getString(k) }
            }
            body = json.optJSONObject(KlaviyoApiRequest.BODY_JSON_KEY)
        }
    }
}

package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest.Companion.URL_JSON_KEY
import com.klaviyo.core.Registry
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
            UnregisterPushTokenApiRequest::class.simpleName -> UnregisterPushTokenApiRequest(
                time,
                uuid
            )
            AggregateEventApiRequest::class.simpleName -> AggregateEventApiRequest(time, uuid)
            UniversalClickTrackRequest::class.simpleName -> UniversalClickTrackRequest(time, uuid)
            FetchGeofencesRequest::class.simpleName -> FetchGeofencesRequest(time, uuid)
            else -> KlaviyoApiRequest(urlPath, method, time, uuid)
        }.apply {
            baseUrl = json.optString(URL_JSON_KEY, Registry.config.baseUrl)
            headers.replaceAllWith(
                json.getJSONObject(KlaviyoApiRequest.HEADERS_JSON_KEY).let {
                    it.keys().asSequence().associateWith { k -> it.getString(k) }.toMap()
                }
            )
            query = json.getJSONObject(KlaviyoApiRequest.QUERY_JSON_KEY).let {
                it.keys().asSequence().associateWith { k -> it.getString(k) }
            }
            body = json.optJSONObject(KlaviyoApiRequest.BODY_JSON_KEY)
        }
    }
}

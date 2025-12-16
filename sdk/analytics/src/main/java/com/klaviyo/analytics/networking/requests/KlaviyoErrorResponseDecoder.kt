package com.klaviyo.analytics.networking.requests

import com.klaviyo.core.Registry
import com.klaviyo.core.utils.JSONUtil.getStringNullable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object KlaviyoErrorResponseDecoder {

    /**
     * Construct an Error Response from a JSON object
     *
     * @return ErrorResponse from the JSON
     */
    internal fun fromJson(json: JSONObject): KlaviyoErrorResponse {
        val errorsJsonArray: JSONArray = try {
            json.getJSONArray(KlaviyoErrorResponse.ERRORS)
        } catch (_: JSONException) {
            JSONArray()
        }
        val errorsList = mutableListOf<KlaviyoError>()
        try {
            for (errorJsonIndex in 0 until errorsJsonArray.length()) {
                val errorJson = errorsJsonArray.getJSONObject(errorJsonIndex)
                errorsList.add(
                    KlaviyoError(
                        id = errorJson.getStringNullable(KlaviyoErrorResponse.ID),
                        status = errorJson.getInt(KlaviyoErrorResponse.STATUS),
                        title = errorJson.getStringNullable(KlaviyoErrorResponse.TITLE),
                        detail = errorJson.getStringNullable(KlaviyoErrorResponse.DETAIL),
                        source = errorJson.optJSONObject(KlaviyoErrorResponse.SOURCE)?.let {
                            KlaviyoErrorSource(
                                it.getStringNullable(KlaviyoErrorResponse.POINTER)
                            )
                        }
                    )
                )
            }
        } catch (e: Exception) {
            Registry.log.error("Failed to decode error response", e)
        }
        return KlaviyoErrorResponse(errorsList)
    }
}

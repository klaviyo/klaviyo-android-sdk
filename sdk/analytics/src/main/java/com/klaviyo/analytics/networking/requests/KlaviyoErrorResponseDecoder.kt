package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.networking.requests.JSONUtil.getStringNullable
import org.json.JSONArray
import org.json.JSONObject

internal object KlaviyoErrorResponseDecoder {

    /**
     * Construct an Error Response from a JSON object
     *
     * @return ErrorResponse from the JSON
     */
    internal fun fromJson(json: JSONObject): ErrorResponse {
        val errorsJsonArray: JSONArray = json.getJSONArray(ErrorResponse.ERRORS)
        val errorsList = mutableListOf<KlaviyoError>()
        for (errorJsonIndex in 0 until errorsJsonArray.length()) {
            val errorJson = errorsJsonArray.getJSONObject(errorJsonIndex)
            errorsList.add(
                KlaviyoError(
                    id = errorJson.getStringNullable(ErrorResponse.ID),
                    status = errorJson.getInt(ErrorResponse.STATUS),
                    title = errorJson.getStringNullable(ErrorResponse.TITLE),
                    detail = errorJson.getStringNullable(ErrorResponse.DETAIL),
                    source = errorJson.getJSONObject(ErrorResponse.SOURCE)?.let {
                        KlaviyoErrorSource(
                            it.getStringNullable(ErrorResponse.POINTER)
                        )
                    }
                )
            )
        }
        return ErrorResponse(errorsList)
    }
}

package com.klaviyo.analytics.networking.requests

import org.json.JSONObject

object FullFormsApiResponseDecoder {

    /**
     * We'll be updating the endpoint to only return one form for mobile clients
     * but for now, this uses the web version and only grabs the top form
     */
    fun onlyFirstForm(json: JSONObject): JSONObject =
        JSONObject().apply {
            put(
                FullsFormsResponse.FULL_FORMS,
                json.getJSONArray(FullsFormsResponse.FULL_FORMS)[0]
            )
            put(
                FullsFormsResponse.FORM_SETTINGS,
                json.getJSONObject(FullsFormsResponse.FORM_SETTINGS)
            )
            put(
                FullsFormsResponse.DYNAMIC_INFO_CONFIG,
                json.getJSONObject(FullsFormsResponse.DYNAMIC_INFO_CONFIG)
            )
        }
}

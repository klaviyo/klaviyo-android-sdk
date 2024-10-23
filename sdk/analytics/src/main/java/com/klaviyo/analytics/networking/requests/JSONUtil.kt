package com.klaviyo.analytics.networking.requests

import org.json.JSONObject

internal object JSONUtil {

    /**
     * Using this util since built-in optString gets scared with a null default value
     */
    fun JSONObject.getStringNullable(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}

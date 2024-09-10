package com.klaviyo.analytics.networking.requests

import org.json.JSONObject

internal object JSONUtil {

    fun JSONObject.getStringNullable(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}

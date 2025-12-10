package com.klaviyo.analytics.networking.requests

import java.io.Serializable
import org.json.JSONArray
import org.json.JSONObject

internal object JSONUtil {

    /**
     * Using this util since built-in optString gets scared with a null default value
     */
    fun JSONObject.getStringNullable(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    /**
     * Converts a [JSONArray] to a typesafe serializable Kotlin [Array]
     */
    fun JSONArray.toArray(): Array<Serializable?> = List(length()) { index ->
        opt(index).jsonValueToSerializable()
    }.toTypedArray()

    /**
     * Converts a [JSONObject] to a typesafe serializable [HashMap]
     */
    fun JSONObject.toHashMap(): HashMap<String, Serializable?> =
        HashMap<String, Serializable?>().also { map ->
            keys().forEach { key ->
                map[key] = opt(key).jsonValueToSerializable()
            }
        }

    /**
     * Converts a JSON value to [Serializable] in a typesafe manner
     */
    private fun Any?.jsonValueToSerializable(): Serializable? = when (this) {
        null -> null
        is Serializable -> this
        JSONObject.NULL -> null
        is JSONObject -> toHashMap()
        is JSONArray -> toArray()
        else -> toString()
    }
}

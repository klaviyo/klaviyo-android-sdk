package com.klaviyo.forms.bridge

import org.json.JSONArray
import org.json.JSONObject

/**
 * Data class to represent a handshake item
 */
internal data class HandshakeSpec(
    val type: String,
    val version: Int
) {
    companion object {
        const val SPEC_TYPE_KEY = "type"
        const val SPEC_VERSION_KEY = "version"
    }
}

internal fun List<HandshakeSpec>.compileJson(): String {
    val jsonArray = JSONArray()
    for (spec in this) {
        val jsonObject = JSONObject()
        jsonObject.put(HandshakeSpec.SPEC_TYPE_KEY, spec.type)
        jsonObject.put(HandshakeSpec.SPEC_VERSION_KEY, spec.version)
        jsonArray.put(jsonObject)
    }
    return jsonArray.toString()
}

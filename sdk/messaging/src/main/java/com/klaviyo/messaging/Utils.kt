package com.klaviyo.messaging

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.core.Registry
import java.io.Serializable
import org.json.JSONObject

internal fun JSONObject.getProperties(): Map<EventKey, Serializable> {
    val map = mutableMapOf<EventKey, Serializable>()
    // todo this might change depending on the message bus contract
    val propertyMap = this.getJSONObject(IAF_PROPERTIES_KEY)
    propertyMap.keys().forEach {
        try {
            map[EventKey.CUSTOM(it)] = propertyMap.getString(it)
        } catch (e: Exception) {
            Registry.log.error("Failed to write property $it to profile event payload", e)
        }
    }
    return map
}

internal fun decodeWebviewMessage(webMessage: String): KlaviyoWebFormMessageType {
    val jsonMessage = JSONObject(webMessage)
    val jsonData = jsonMessage.optJSONObject(IAF_MESSAGE_DATA_KEY) ?: JSONObject()

    return when (val type = jsonMessage.optString(IAF_MESSAGE_TYPE_KEY)) {
        IAF_MESSAGE_TYPE_SHOW -> KlaviyoWebFormMessageType.Show
        IAF_MESSAGE_TYPE_CLOSE -> KlaviyoWebFormMessageType.Close
        IAF_MESSAGE_TYPE_PROFILE_EVENT -> {
            KlaviyoWebFormMessageType.ProfileEvent(
                event = jsonData.optString(IAF_EVENT_NAME_KEY)?.let {
                    Event(it, properties = jsonData.getProperties())
                } ?: throw IllegalStateException("Missing profile eventName key")
            )
        }
        else -> throw IllegalStateException("Unrecognized message type $type")
    }
}

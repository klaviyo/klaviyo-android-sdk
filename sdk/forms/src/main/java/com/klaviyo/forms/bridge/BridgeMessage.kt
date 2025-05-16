package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.networking.requests.AggregateEventPayload
import com.klaviyo.core.Registry
import java.io.Serializable
import org.json.JSONArray
import org.json.JSONObject

/**
 * This should be updated with any new message types we add coming from the onsite-in-app-forms
 */
internal sealed class BridgeMessage {

    data object JsReady : BridgeMessage()

    data object HandShook : BridgeMessage()

    data class Show(
        val formId: String
    ) : BridgeMessage()

    data class AggregateEventTracked(
        val payload: AggregateEventPayload
    ) : BridgeMessage()

    data class ProfileEvent(
        val event: Event
    ) : BridgeMessage()

    data class DeepLink(
        val route: String
    ) : BridgeMessage()

    data class Close(
        val formId: String
    ) : BridgeMessage()

    data class Abort(
        val reason: String
    ) : BridgeMessage()

    companion object {
        private const val IAF_MESSAGE_DATA_KEY = "data"
        private const val IAF_MESSAGE_TYPE_KEY = "type"
        private const val IAF_TYPE_VERSION_KEY = "version"

        private const val BRIDGE_JS_READY = "jsReady"
        private const val IAF_MESSAGE_HAND_SHOOK = "handShook"
        private const val IAF_MESSAGE_TYPE_SHOW = "formWillAppear"
        private const val IAF_MESSAGE_TYPE_AGGREGATE_EVENT = "trackAggregateEvent"
        private const val IAF_MESSAGE_TYPE_PROFILE_EVENT = "trackProfileEvent"
        private const val IAF_MESSAGE_TYPE_DEEPLINK = "openDeepLink"
        private const val IAF_MESSAGE_TYPE_CLOSE = "formDisappeared"
        private const val IAF_MESSAGE_TYPE_ABORT = "abort"

        /**
         * Handshake data: the types and version numbers of bridge messages that the SDK supports
         */
        internal val handShakeData by lazy {
            JSONArray(
                listOf(
                    mapOf(
                        IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_HAND_SHOOK,
                        IAF_TYPE_VERSION_KEY to 1
                    ),
                    mapOf(
                        IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_SHOW,
                        IAF_TYPE_VERSION_KEY to 1
                    ),
                    mapOf(
                        IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_AGGREGATE_EVENT,
                        IAF_TYPE_VERSION_KEY to 1
                    ),
                    mapOf(
                        IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_PROFILE_EVENT,
                        IAF_TYPE_VERSION_KEY to 1
                    ),
                    mapOf(
                        IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_DEEPLINK,
                        IAF_TYPE_VERSION_KEY to 1
                    ),
                    mapOf(
                        IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_CLOSE,
                        IAF_TYPE_VERSION_KEY to 1
                    ),
                    mapOf(
                        IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_ABORT,
                        IAF_TYPE_VERSION_KEY to 1
                    )
                )
            ).toString()
        }

        /**
         * Parse a native bridge message string into a [BridgeMessage]
         *
         * @throws IllegalStateException for unexpected message strings
         */
        fun decodeWebviewMessage(message: String): BridgeMessage {
            val jsonMessage = JSONObject(message)
            val jsonData = jsonMessage.optJSONObject(IAF_MESSAGE_DATA_KEY) ?: JSONObject()

            return when (val type = jsonMessage.optString(IAF_MESSAGE_TYPE_KEY)) {
                BRIDGE_JS_READY -> JsReady

                IAF_MESSAGE_HAND_SHOOK -> HandShook

                IAF_MESSAGE_TYPE_SHOW -> Show(
                    formId = jsonData.optString("formId").ifEmpty { "" }
                )

                IAF_MESSAGE_TYPE_AGGREGATE_EVENT -> AggregateEventTracked(
                    payload = jsonData
                )

                IAF_MESSAGE_TYPE_PROFILE_EVENT -> ProfileEvent(
                    event = Event(
                        jsonData.getString("metric"),
                        properties = jsonData.getEventProperties()
                    )
                )

                IAF_MESSAGE_TYPE_DEEPLINK -> DeepLink(
                    route = jsonData.getDeepLink()
                )

                IAF_MESSAGE_TYPE_CLOSE -> Close(
                    formId = jsonData.optString("formId").ifEmpty { "" }
                )

                IAF_MESSAGE_TYPE_ABORT -> Abort(
                    reason = jsonData.optString("reason").ifEmpty { "Unknown" }
                )

                else -> throw IllegalStateException("Unrecognized message type $type")
            }
        }

        /**
         * Parse [Event] properties for a [ProfileEvent] message
         */
        private fun JSONObject.getEventProperties(): Map<EventKey, Serializable> {
            val map = mutableMapOf<EventKey, Serializable>()
            val propertyMap = getJSONObject("properties")
            propertyMap.keys().forEach {
                try {
                    map[EventKey.CUSTOM(it)] = propertyMap.get(it) as Serializable
                } catch (e: Exception) {
                    Registry.log.error("Failed to write property $it to profile event payload", e)
                }
            }
            return map
        }

        /**
         * Parse out the android platform deep link
         */
        private fun JSONObject.getDeepLink(): String {
            val routeString = getString("android")
            if (routeString.isNullOrEmpty()) {
                throw IllegalStateException("No android deeplink found in js payload")
            }
            return routeString
        }
    }
}

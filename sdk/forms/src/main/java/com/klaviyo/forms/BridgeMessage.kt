package com.klaviyo.forms

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.networking.requests.AggregateEventPayload
import com.klaviyo.core.Registry
import java.io.Serializable
import org.json.JSONArray
import org.json.JSONObject

/**
 * Decoding top-level message types
 */
internal const val IAF_MESSAGE_DATA_KEY = "data"
internal const val IAF_MESSAGE_TYPE_KEY = "type"
internal const val IAF_TYPE_VERSION_KEY = "version"

internal const val IAF_MESSAGE_TYPE_SHOW = "formWillAppear"
internal const val IAF_MESSAGE_TYPE_CLOSE = "formDisappeared"
internal const val IAF_MESSAGE_TYPE_PROFILE_EVENT = "trackProfileEvent"
internal const val IAF_MESSAGE_TYPE_AGGREGATE_EVENT = "trackAggregateEvent"
internal const val IAF_MESSAGE_TYPE_DEEPLINK = "openDeepLink"
internal const val IAF_MESSAGE_TYPE_ABORT = "abort"
internal const val IAF_MESSAGE_HAND_SHOOK = "handShook"

/**
 * Abort fields
 */
internal const val IAF_ABORT_REASON = "reason"

/**
 * Profile event constants
 */
internal const val IAF_METRIC_KEY = "metric"
internal const val IAF_PROPERTIES_KEY = "properties"

/**
 * Deep Link fields
 */
internal const val IAF_DEEPLINK_ANDROID = "android"

/**
 * This should be updated with any new message types we add coming from the onsite-in-app-forms
 */
sealed class BridgeMessage {
    data object HandShook : BridgeMessage()

    data object Show : BridgeMessage()

    data class AggregateEventTracked(
        val payload: AggregateEventPayload
    ) : BridgeMessage()

    data class ProfileEvent(
        val event: Event
    ) : BridgeMessage()

    data class DeepLink(
        val route: String
    ) : BridgeMessage()

    data object Close : BridgeMessage()

    data class Abort(
        val reason: String
    ) : BridgeMessage()

    companion object {
        /**
         * Handshake data: the types and version numbers of bridge messages that the SDK supports
         */
        internal val handShakeData by lazy {
            JSONArray(
                listOf(
                    mapOf(
                        IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_SHOW,
                        IAF_TYPE_VERSION_KEY to 1
                    ),
                    mapOf(
                        IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_CLOSE,
                        IAF_TYPE_VERSION_KEY to 1
                    ),
                    mapOf(
                        IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_PROFILE_EVENT,
                        IAF_TYPE_VERSION_KEY to 1
                    ),
                    mapOf(
                        IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_AGGREGATE_EVENT,
                        IAF_TYPE_VERSION_KEY to 1
                    ),
                    mapOf(
                        IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_DEEPLINK,
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
                IAF_MESSAGE_HAND_SHOOK -> HandShook
                IAF_MESSAGE_TYPE_SHOW -> Show
                IAF_MESSAGE_TYPE_AGGREGATE_EVENT -> {
                    AggregateEventTracked(
                        payload = jsonData
                    )
                }
                IAF_MESSAGE_TYPE_PROFILE_EVENT -> {
                    ProfileEvent(
                        event = Event(
                            jsonData.getString(IAF_METRIC_KEY),
                            properties = jsonData.getEventProperties()
                        )
                    )
                }
                IAF_MESSAGE_TYPE_DEEPLINK -> {
                    val routeString = jsonData.getString(IAF_DEEPLINK_ANDROID)
                    if (routeString.isNullOrEmpty()) {
                        throw IllegalStateException("No android deeplink found in js payload")
                    } else {
                        DeepLink(
                            route = routeString
                        )
                    }
                }
                IAF_MESSAGE_TYPE_CLOSE -> Close
                IAF_MESSAGE_TYPE_ABORT -> Abort(
                    reason = jsonData.optString(IAF_ABORT_REASON).ifEmpty { "Unknown" }
                )
                else -> throw IllegalStateException("Unrecognized message type $type")
            }
        }

        /**
         * Parse [Event] properties for a [ProfileEvent] message
         */
        fun JSONObject.getEventProperties(): Map<EventKey, Serializable> {
            val map = mutableMapOf<EventKey, Serializable>()
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
    }
}

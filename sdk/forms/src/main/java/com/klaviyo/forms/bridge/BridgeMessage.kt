package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.networking.requests.AggregateEventPayload
import java.io.Serializable
import org.json.JSONObject

/**
 * This should be updated with any new message types we add coming from the onsite-in-app-forms
 */
internal sealed class BridgeMessage {

    data object JsReady : BridgeMessage()

    data object HandShook : BridgeMessage()

    data class FormWillAppear(
        val formId: String
    ) : BridgeMessage()

    data class TrackAggregateEvent(
        val payload: AggregateEventPayload
    ) : BridgeMessage()

    data class TrackProfileEvent(
        val event: Event
    ) : BridgeMessage()

    data class OpenDeepLink(
        val route: String
    ) : BridgeMessage()

    data class FormDisappeared(
        val formId: String
    ) : BridgeMessage()

    data class Abort(
        val reason: String
    ) : BridgeMessage()

    companion object {
        private const val MESSAGE_TYPE_KEY = HandshakeSpec.SPEC_TYPE_KEY
        private const val MESSAGE_DATA_KEY = "data"

        internal val handShakeData by lazy {
            listOf(
                HandshakeSpec(keyName<HandShook>(), 1),
                HandshakeSpec(keyName<FormWillAppear>(), 1),
                HandshakeSpec(keyName<TrackAggregateEvent>(), 1),
                HandshakeSpec(keyName<TrackProfileEvent>(), 1),
                HandshakeSpec(keyName<OpenDeepLink>(), 1),
                HandshakeSpec(keyName<FormDisappeared>(), 1),
                HandshakeSpec(keyName<Abort>(), 1)
            )
        }

        /**
         * Parse a native bridge message string into a [BridgeMessage]
         *
         * @throws IllegalStateException for unexpected message strings
         */
        fun decodeWebviewMessage(message: String): BridgeMessage {
            val jsonMessage = JSONObject(message)
            val jsonData = jsonMessage.optJSONObject(MESSAGE_DATA_KEY) ?: JSONObject()

            return when (val type = jsonMessage.optString(MESSAGE_TYPE_KEY)) {
                keyName<JsReady>() -> JsReady

                keyName<HandShook>() -> HandShook

                keyName<FormWillAppear>() -> FormWillAppear(
                    formId = jsonData.optString("formId").ifEmpty { "" }
                )

                keyName<TrackAggregateEvent>() -> TrackAggregateEvent(
                    payload = jsonData
                )

                keyName<TrackProfileEvent>() -> TrackProfileEvent(
                    event = Event(
                        jsonData.getString("metric"),
                        properties = jsonData.getEventProperties()
                    )
                )

                keyName<OpenDeepLink>() -> OpenDeepLink(
                    route = jsonData.getDeepLink()
                )

                keyName<FormDisappeared>() -> FormDisappeared(
                    formId = jsonData.optString("formId").ifEmpty { "" }
                )

                keyName<Abort>() -> Abort(
                    reason = jsonData.optString("reason").ifEmpty { "Unknown" }
                )

                else -> throw IllegalStateException("Unrecognized message type $type")
            }
        }

        /**
         * Parse [Event] properties for a [TrackProfileEvent] message
         */
        private fun JSONObject.getEventProperties(): Map<EventKey, Serializable> {
            val map = mutableMapOf<EventKey, Serializable>()
            val propertyMap = getJSONObject("properties")
            propertyMap.keys().forEach {
                (propertyMap.opt(it) as? Serializable)?.let { value ->
                    map[EventKey.CUSTOM(it)] = value
                }
            }
            return map
        }

        /**
         * Parse out the android platform deep link
         */
        private fun JSONObject.getDeepLink(): String {
            val routeString = optString("android")
            if (routeString.isNullOrEmpty()) {
                throw IllegalStateException("No android deeplink found in js payload")
            }
            return routeString
        }
    }
}

private inline fun <reified T : BridgeMessage> keyName(): String =
    T::class.java.simpleName.let { it[0].lowercaseChar() + it.substring(1) }

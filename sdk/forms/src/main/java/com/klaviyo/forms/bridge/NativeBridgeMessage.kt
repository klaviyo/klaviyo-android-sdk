package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.networking.requests.AggregateEventPayload
import java.io.Serializable
import org.json.JSONObject

/**
 * Encapsulates messages sent from JS to SDK via the NativeBridge, i.e. [NativeBridge].
 * This should be updated with any new message types we add coming from the onsite-in-app-forms
 * By convention, class names should be upper camelcased version of the message type.
 */
internal sealed class NativeBridgeMessage {

    /**
     * Sent from onsite-bridge.js when that local JS asset has initialized
     */
    data object JsReady : NativeBridgeMessage()

    /**
     * Sent from the onsite-in-app-forms when the NativeBridge handshake has been completed,
     * indicating the fender package is fully initialized.
     */
    data object HandShook : NativeBridgeMessage()

    /**
     * Sent from the onsite-in-app-forms when a form is about to appear as a signal to present the webview
     *
     * @param formId The form ID of the form that is appearing
     */
    data class FormWillAppear(
        val formId: String
    ) : NativeBridgeMessage()

    /**
     * Sent from the onsite-in-app-forms when an aggregate event is tracked
     * so that the SDK can send it via the native API queue
     *
     * @param payload The payload of the aggregate event, to be sent unmodified
     */
    data class TrackAggregateEvent(
        val payload: AggregateEventPayload
    ) : NativeBridgeMessage()

    /**
     * Sent from the onsite-in-app-forms when a profile event is tracked
     * so that the SDK can send it via the native API queue
     *
     * @param event The event to be sent, the SDK should attach the profile to it
     */
    data class TrackProfileEvent(
        val event: Event
    ) : NativeBridgeMessage()

    /**
     * Sent from the onsite-in-app-forms when a deep link is opened
     *
     * @param route The deep link route to be opened (usually a URL)
     */
    data class OpenDeepLink(
        val route: String
    ) : NativeBridgeMessage()

    /**
     * Sent from the onsite-in-app-forms when a form is closed as a signal to dismiss the webview
     *
     * @param formId The form ID of the form that is disappearing
     */
    data class FormDisappeared(
        val formId: String
    ) : NativeBridgeMessage()

    /**
     * Sent from the onsite-in-app-forms when an irrecoverable error occurs and the webview should be closed
     */
    data class Abort(
        val reason: String
    ) : NativeBridgeMessage()

    companion object {
        private const val MESSAGE_TYPE_KEY = HandshakeSpec.SPEC_TYPE_KEY
        private const val MESSAGE_DATA_KEY = "data"

        /**
         * Convert a [NativeBridgeMessage] subclass to its "type" by convention (lower camel case)
         */
        private inline fun <reified T : NativeBridgeMessage> keyName(): String =
            T::class.java.simpleName.let { it[0].lowercaseChar() + it.substring(1) }

        /**
         * Compile a list of [HandshakeSpec] to be injected into the webview
         */
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
         * Parse a native bridge message string into a [NativeBridgeMessage]
         *
         * @throws IllegalStateException for unexpected message strings
         */
        fun decodeWebviewMessage(message: String): NativeBridgeMessage {
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

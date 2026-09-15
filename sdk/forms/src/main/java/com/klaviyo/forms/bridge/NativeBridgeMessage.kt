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
     * Sent from onsite-in-app-forms when the NativeBridge handshake has been completed,
     * indicating the fender package is fully initialized.
     */
    data object HandShook : NativeBridgeMessage()

    /**
     * Sent from the onsite-in-app-forms when a form is about to appear as a signal to present the webview
     *
     * @param formId The form ID of the form that is appearing
     * @param formName The name of the form that is appearing
     * @param layout The optional layout configuration for the form
     */
    data class FormWillAppear(
        val formId: FormId,
        val formName: String,
        val layout: FormLayout? = null
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
     * Sent from the onsite-in-app-forms when a form CTA navigates to a URL. Carries both in-app
     * deep links and external web/system URLs; [openExternally] distinguishes them (onsite
     * openDeepLink v3).
     *
     * @param route The URL to open (from the platform `android` field), or null if none is configured
     * @param formId The form ID of the form that triggered the CTA
     * @param formName The name of the form that triggered the CTA
     * @param buttonLabel The text label of the CTA button that was clicked
     * @param openExternally When true, route the URL to its external handler (a browser for
     * `http`/`https`, or the mail, dialer, or SMS app for `mailto:`/`tel:`/`sms:`/`smsto:`),
     * bypassing any registered deep link handler, gated by the scheme allowlist. When false,
     * route it as an in-app deep link.
     */
    data class OpenDeepLink(
        val route: String?,
        val formId: FormId,
        val formName: String,
        val buttonLabel: String,
        val openExternally: Boolean
    ) : NativeBridgeMessage()

    /**
     * Sent from the onsite-in-app-forms when a form is closed as a signal to dismiss the webview
     *
     * @param formId The form ID of the form that is disappearing
     * @param formName The name of the form that is disappearing
     */
    data class FormDisappeared(
        val formId: FormId,
        val formName: String
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
                HandshakeSpec(keyName<FormWillAppear>(), 2),
                HandshakeSpec(keyName<TrackAggregateEvent>(), 1),
                HandshakeSpec(keyName<TrackProfileEvent>(), 1),
                // v2 issues deep link after closing the form (v1 was before close, causing a timing issue).
                // v3 carries both deep links and external URLs in one message, keyed by `openExternally`.
                HandshakeSpec(keyName<OpenDeepLink>(), 3),
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
                    formId = jsonData.optString("formId"),
                    formName = jsonData.optString("formName"),
                    layout = FormLayout.fromJson(jsonData.optJSONObject("layout"))
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
                    route = jsonData.getDeepLink(),
                    formId = jsonData.optString("formId"),
                    formName = jsonData.optString("formName"),
                    buttonLabel = jsonData.optString("buttonLabel"),
                    openExternally = jsonData.optBoolean("openExternally", false)
                )

                keyName<FormDisappeared>() -> FormDisappeared(
                    formId = jsonData.optString("formId"),
                    formName = jsonData.optString("formName")
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
         * Parse out the android platform deep link, returning null if not present or empty
         */
        private fun JSONObject.getDeepLink(): String? =
            optString("android").takeIf { it.isNotEmpty() }
    }
}

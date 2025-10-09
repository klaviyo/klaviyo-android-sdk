package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.core.Registry
import com.klaviyo.forms.webview.JavaScriptEvaluator
import org.json.JSONObject

/**
 * API for communicating data and events from native to the onsite-in-app JS module
 * via data attribute setters and event dispatcher functions defined in onsite-bridge.js
 */
internal class KlaviyoJsBridge : JsBridge {
    @Suppress("EnumEntryName", "ktlint:enum-entry-name-case")
    private enum class HelperFunction {
        profileMutation,
        lifecycleEvent,
        openForm,
        closeForm,
        profileEvent,
        setSafeArea
    }

    override val handshake: List<HandshakeSpec> = listOf(
        HandshakeSpec(
            type = HelperFunction.profileMutation.name,
            version = 1
        ),
        HandshakeSpec(
            type = HelperFunction.lifecycleEvent.name,
            version = 1
        ),
        HandshakeSpec(
            type = HelperFunction.closeForm.name,
            version = 1
        ),
        HandshakeSpec(
            type = HelperFunction.profileEvent.name,
            version = 1
        )
    )

    override fun openForm(formId: FormId) = evaluateJavascript(
        HelperFunction.openForm,
        formId
    )

    override fun closeForm(formId: FormId?) = evaluateJavascript(
        HelperFunction.closeForm,
        formId ?: ""
    )

    override fun profileMutation(profile: ImmutableProfile) = evaluateJavascript(
        HelperFunction.profileMutation,
        profile.externalId ?: "",
        profile.email ?: "",
        profile.phoneNumber ?: "",
        profile.anonymousId ?: ""
    )

    override fun lifecycleEvent(type: JsBridge.LifecycleEventType) = evaluateJavascript(
        HelperFunction.lifecycleEvent,
        type.name
    )

    override fun profileEvent(event: Event) {
        evaluateJavascript(
            HelperFunction.profileEvent,
            event.metric.name,
            event.pop(EventKey.EVENT_ID),
            event.pop(EventKey.CUSTOM("_time")),
            event.pop(EventKey.VALUE),
            event.toMap()
        )
    }

    override fun setSafeArea(left: Float, top: Float, right: Float, bottom: Float) =
        evaluateJavascript(
            HelperFunction.setSafeArea,
            left.toString(),
            top.toString(),
            right.toString(),
            bottom.toString()
        )

    /**
     * Evaluates a JS function in the webview with the given arguments
     */
    private fun evaluateJavascript(function: HelperFunction, vararg arguments: Any?) {
        val args = arguments.joinToString(",") { it.toJsonString() }
        val javaScript = "window.$function($args)"

        Registry.get<JavaScriptEvaluator>().evaluateJavascript(javaScript) { result ->
            if (result) {
                Registry.log.verbose("JS $function evaluation succeeded")
            } else {
                Registry.log.error("JS $function evaluation failed")
            }
        }
    }
}

/**
 * Converts a Kotlin object to a JSON-compatible string representation suitable for embedding in JavaScript code.
 * e.g.:
 *  null -> "null"
 *  "hello" -> "\"hello\""
 *  123 -> "123"
 *  true -> "true"
 *  mapOf("key" to "value") -> "{\"key\":\"value\"}"\
 */
private fun Any?.toJsonString(): String = when (this) {
    is String -> JSONObject.quote(this)
    is Number -> this.toString()
    is Boolean -> this.toString()
    is Map<*, *> -> JSONObject(this).toString()
    null -> "null"
    else -> this.toString().let{ str ->
        Registry.log.warning("Potentially unsafe object type for JSON serialization: ${this::class.java}=$str")
        JSONObject.quote(str)
    }
}

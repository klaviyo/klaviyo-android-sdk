package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.core.Registry
import com.klaviyo.forms.webview.JavaScriptEvaluator

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
        // Capture values before any mutations to avoid side effects during argument evaluation
        val metricName = event.metric.name.toJsString()
        val uniqueId = event.uniqueId.toJsStringOrNull()
        val time = event.pop(EventKey.CUSTOM("_time")).toString()
        val value = event.pop(EventKey.VALUE).toString()
        val eventJson = event.toString() // Serialize after removing _time and value

        // JavaScript signature: window.profileEvent = function (metric, uuid, time, value, properties)
        evaluateJavascriptRaw(
            HelperFunction.profileEvent,
            metricName,
            uniqueId,
            time,
            value,
            eventJson // Unquoted JSON string (becomes JS object literal)
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
    private fun evaluateJavascript(function: HelperFunction, vararg arguments: String) {
        val args = arguments.joinToString(",") { "\"$it\"" }
        val javaScript = "window.$function($args)"

        Registry.get<JavaScriptEvaluator>().evaluateJavascript(javaScript) { result ->
            if (result) {
                Registry.log.verbose("JS $function evaluation succeeded")
            } else {
                Registry.log.error("JS $function evaluation failed")
            }
        }
    }

    /**
     * Evaluates a JS function with raw arguments (no automatic quoting applied)
     */
    private fun evaluateJavascriptRaw(function: HelperFunction, vararg arguments: String) {
        val args = arguments.joinToString(",")
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
 * Extension function to convert a Kotlin String to a properly escaped and quoted JavaScript string literal.
 * Handles single quotes by escaping them.
 *
 * Example: "Hello's World" -> "\"Hello\\'s World\""
 */
private fun String.toJsString(): String = "\"${this.replace("'", "\\'")}\""

/**
 * Extension function to convert a nullable Kotlin String to a properly escaped and quoted JavaScript string literal,
 * or "null" if the string is null.
 *
 * Example: "test-uuid" -> "\"test-uuid\""
 * Example: null -> "null"
 */
private fun String?.toJsStringOrNull(): String = this?.toJsString() ?: "null"

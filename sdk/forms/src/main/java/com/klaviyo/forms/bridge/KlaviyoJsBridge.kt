package com.klaviyo.forms.bridge

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
        closeForm
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
}

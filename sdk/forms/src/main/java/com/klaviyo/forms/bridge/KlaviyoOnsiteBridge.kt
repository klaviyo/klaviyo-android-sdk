package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.core.Registry
import com.klaviyo.forms.webview.JavaScriptEvaluator
import com.klaviyo.forms.webview.JsCallback

/**
 * API for communicating data and events from native to the onsite-in-app JS module
 * via data attribute setters and event dispatcher functions defined in onsite-bridge.js
 */
internal class KlaviyoOnsiteBridge : OnsiteBridge {
    @Suppress("EnumEntryName", "ktlint:enum-entry-name-case")
    private enum class HelperFunction {
        setProfile,
        dispatchLifecycleEvent
    }

    override fun setProfile(profile: ImmutableProfile) = evaluateJavascript(
        HelperFunction.setProfile,
        profile.externalId ?: "",
        profile.email ?: "",
        profile.phoneNumber ?: "",
        profile.anonymousId ?: ""
    )

    override fun dispatchLifecycleEvent(
        type: OnsiteBridge.LifecycleEventType,
        session: OnsiteBridge.LifecycleSessionBehavior,
        callback: JsCallback
    ) = evaluateJavascript(
        HelperFunction.dispatchLifecycleEvent,
        type.name,
        session.name,
        callback = callback
    )

    /**
     * Evaluates a JS function in the webview with the given arguments
     */
    private fun evaluateJavascript(
        function: HelperFunction,
        vararg arguments: String,
        callback: JsCallback = {}
    ) {
        val args = arguments.joinToString(",") { "\"$it\"" }
        val javaScript = "window.$function($args)"

        Registry.get<JavaScriptEvaluator>().evaluateJavascript(javaScript) { result ->
            callback(result)

            if (result) {
                Registry.log.verbose("JS $function evaluation succeeded")
            } else {
                Registry.log.error("JS $function evaluation failed")
            }
        }
    }
}

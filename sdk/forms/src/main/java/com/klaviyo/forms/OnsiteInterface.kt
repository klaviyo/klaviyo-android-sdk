package com.klaviyo.forms

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.core.Registry

/**
 * Interface for communicating into the Klaviyo webview via JS functions established in klaviyo-forms-helpers.js
 */
@Suppress("EnumEntryName", "ktlint:enum-entry-name-case")
class OnsiteInterface(
    private val evaluator: JavaScriptEvaluator = Registry.get()
) {
    private enum class HelperFunction {
        setProfile,
        dispatchLifecycleEvent,
        dispatchAnalyticsEvent
    }

    enum class LifecycleEventType {
        background,
        foreground
    }

    enum class LifecycleSessionBehavior {
        persist,
        restore,
        purge
    }

    fun setProfile(profile: ImmutableProfile) =
        evaluateJavascript(
            HelperFunction.setProfile,
            profile.externalId ?: "",
            profile.email ?: "",
            profile.phoneNumber ?: "",
            profile.anonymousId ?: ""
        )

    fun dispatchLifecycleEvent(type: LifecycleEventType, session: LifecycleSessionBehavior) =
        evaluateJavascript(HelperFunction.dispatchLifecycleEvent, type.name, session.name)

    // TODO properties to string
    fun dispatchAnalyticsEvent(event: Event) =
        evaluateJavascript(
            HelperFunction.dispatchAnalyticsEvent,
            event.metric.name,
            event.toMap().toString()
        )

    /**
     * Evaluates a JS function in the webview with the given arguments
     */
    private fun evaluateJavascript(function: HelperFunction, vararg arguments: String) {
        val args = arguments.joinToString(",") { "\"$it\"" }
        val javaScript = "window.$function($args)"

        evaluator.evaluateJavascript(javaScript) { result ->
            if (result) {
                Registry.log.verbose("JS $function evaluation succeeded")
            } else {
                Registry.log.error("JS $function evaluation failed: $javaScript")
            }
        }
    }
}

package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.ImmutableProfile

typealias FormId = String

/**
 * API for communicating data and events from native to the onsite-in-app JS module
 */
@Suppress("EnumEntryName", "ktlint:enum-entry-name-case")
internal interface JsBridge {
    /**
     * Lifecycle event types supported by [lifecycleEvent]
     */
    enum class LifecycleEventType {
        background,
        foreground
    }

    /**
     * Handshake data indicating the message types/versions that the SDK supports sending over the JsBridge
     */
    val handshake: List<HandshakeSpec>

    /**
     * Inject profile data into the webview as data attributes
     */
    fun profileMutation(profile: ImmutableProfile)

    /**
     * Dispatch lifecycle events for onsite JS package to consume
     */
    fun lifecycleEvent(type: LifecycleEventType)

    /**
     * Open a form in the webview by its ID
     * At this time, this is only used for internal testing
     * Opening a form by ID is not a supported feature in the public API.
     */
    fun openForm(formId: FormId)

    /**
     * Close a form in the webview by [FormId]
     * If no ID provided, close any currently open forms.
     */
    fun closeForm(formId: FormId? = null)
}

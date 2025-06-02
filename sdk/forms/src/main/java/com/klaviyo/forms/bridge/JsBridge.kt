package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.ImmutableProfile

typealias FormId = String

typealias FormVersionId = Int

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
     */
    fun openForm(formId: FormId)

    /**
     * Close a form in the webview by [FormVersionId] and/or [FormId].
     * If neither is provided, close any currently open forms.
     */
    fun closeForm(formId: FormId?, formVersionId: FormVersionId?)
}

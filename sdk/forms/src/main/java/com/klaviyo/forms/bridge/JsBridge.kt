package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.ImmutableProfile

/**
 * API for communicating data and events from native to the onsite-in-app JS module
 */
@Suppress("EnumEntryName", "ktlint:enum-entry-name-case")
internal interface JsBridge {

    enum class LifecycleEventType {
        background,
        foreground
    }

    /**
     * Inject profile data into the webview as data attributes
     */
    fun setProfile(profile: ImmutableProfile)

    /**
     * Dispatch lifecycle events for onsite JS package to consume
     */
    fun dispatchLifecycleEvent(type: LifecycleEventType)
}

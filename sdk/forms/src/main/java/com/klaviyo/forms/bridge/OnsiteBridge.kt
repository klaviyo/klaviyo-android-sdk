package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.ImmutableProfile

/**
 * API for communicating data and events from native to the onsite-in-app JS module
 */
@Suppress("EnumEntryName", "ktlint:enum-entry-name-case")
internal interface OnsiteBridge {

    enum class LifecycleEventType {
        background,
        foreground
    }

    enum class LifecycleSessionBehavior {
        persist,
        restore,
        purge
    }

    /**
     * Inject profile data into the webview as data attributes
     */
    fun setProfile(profile: ImmutableProfile)

    /**
     * Dispatch lifecycle events for onsite JS package to consume
     */
    fun dispatchLifecycleEvent(type: LifecycleEventType, session: LifecycleSessionBehavior)

    /**
     * Dispatch analytics events for onsite JS package to consume
     */
    fun dispatchAnalyticsEvent(event: Event)
}

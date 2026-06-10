package com.klaviyo.core

/**
 * Compile-time constants shared across SDK modules
 */
object Constants {
    /**
     * Package prefix used for Klaviyo intent extras and data keys
     */
    const val PACKAGE_PREFIX = "com.klaviyo."

    /**
     * Key-value pairs get special treatment in a few places across multiple packages
     */
    const val KEY_VALUE_PAIRS = "key_value_pairs"

    /**
     * Klaviyo push messages contain metadata to associate an event with its original transmission
     */
    const val TRACKING_PARAMETER = "_k"

    /**
     * Intent extra key for the notification tag, used to dismiss the notification
     * when an action button is tapped and [handlePush] processes the intent.
     *
     * Uses [INTERNAL_PREFIX] instead of [PACKAGE_PREFIX] to avoid being swept into
     * analytics event properties by [appendKlaviyoExtras].
     */
    private const val INTERNAL_PREFIX = "_klaviyo."
    const val NOTIFICATION_TAG_EXTRA = INTERNAL_PREFIX + "notification_tag"

    /**
     * Fixed notification ID used in all notify/cancel calls.
     * Notifications are uniquely identified by their string tag, not this ID.
     */
    const val NOTIFICATION_ID = 0
}

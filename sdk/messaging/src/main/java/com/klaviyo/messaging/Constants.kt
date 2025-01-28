package com.klaviyo.messaging

/**
 * Fields to replace in the HTML template
 */
internal const val IAF_SDK_NAME_PLACEHOLDER = "SDK_NAME"
internal const val IAF_SDK_VERSION_PLACEHOLDER = "SDK_VERSION"
internal const val IAF_PUBLIC_KEY_PLACEHOLDER = "KLAVIYO_PUBLIC_KEY_PLACEHOLDER"

/**
 * Decoding top-level message types
 */
internal const val IAF_MESSAGE_DATA_KEY = "data"
internal const val IAF_MESSAGE_TYPE_KEY = "type"
internal const val IAF_MESSAGE_TYPE_SHOW = "formDidAppear"
internal const val IAF_MESSAGE_TYPE_CLOSE = "formDidClose"
internal const val IAF_MESSAGE_TYPE_PROFILE_EVENT = "profileEventTracked"
internal const val IAF_MESSAGE_TYPE_AGGREGATE_EVENT = "aggregateEventTracked"
internal const val IAF_MESSAGE_TYPE_DEEPLINK = "deepLinkShouldOpen"

/**
 * Profile event constants
 */
internal const val IAF_METRIC_KEY = "metric"
internal const val IAF_PROPERTIES_KEY = "properties"

/**
 * Deep Link fields
 */
internal const val IAF_DEEPLINK_ANDROID = "android"

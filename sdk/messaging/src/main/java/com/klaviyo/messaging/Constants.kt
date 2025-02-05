package com.klaviyo.messaging

import org.json.JSONArray

/**
 * Fields to replace in the HTML template
 */
internal const val IAF_BRIDGE_NAME_PLACEHOLDER = "BRIDGE_NAME"
internal const val IAF_BRIDGE_NAME = "KlaviyoNativeBridge"
internal const val IAF_SDK_NAME_PLACEHOLDER = "SDK_NAME"
internal const val IAF_SDK_VERSION_PLACEHOLDER = "SDK_VERSION"
internal const val IAF_HANDSHAKE_PLACEHOLDER = "IAF_HANDSHAKE"
internal const val IAF_PUBLIC_KEY_PLACEHOLDER = "KLAVIYO_PUBLIC_KEY_PLACEHOLDER"

/**
 * Decoding top-level message types
 */
internal const val IAF_MESSAGE_DATA_KEY = "data"
internal const val IAF_MESSAGE_TYPE_KEY = "type"
internal const val IAF_TYPE_VERSION_KEY = "version"

internal const val IAF_MESSAGE_TYPE_SHOW = "formAppeared"
internal const val IAF_MESSAGE_TYPE_CLOSE = "formDisappeared"
internal const val IAF_MESSAGE_TYPE_PROFILE_EVENT = "trackProfileEvent"
internal const val IAF_MESSAGE_TYPE_AGGREGATE_EVENT = "trackAggregateEvent"
internal const val IAF_MESSAGE_TYPE_DEEPLINK = "openDeepLink"
internal const val IAF_MESSAGE_TYPE_ABORT = "abort"

internal val IAF_HANDSHAKE by lazy {
    JSONArray(
        listOf(
            mapOf(
                IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_SHOW,
                IAF_TYPE_VERSION_KEY to 1
            ),
            mapOf(
                IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_CLOSE,
                IAF_TYPE_VERSION_KEY to 1
            ),
            mapOf(
                IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_PROFILE_EVENT,
                IAF_TYPE_VERSION_KEY to 1
            ),
            mapOf(
                IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_AGGREGATE_EVENT,
                IAF_TYPE_VERSION_KEY to 1
            ),
            mapOf(
                IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_DEEPLINK,
                IAF_TYPE_VERSION_KEY to 1
            ),
            mapOf(
                IAF_MESSAGE_TYPE_KEY to IAF_MESSAGE_TYPE_ABORT,
                IAF_TYPE_VERSION_KEY to 1
            )
        )
    ).toString()
}

/**
 * Profile event constants
 */
internal const val IAF_METRIC_KEY = "metric"
internal const val IAF_PROPERTIES_KEY = "properties"

/**
 * Deep Link fields
 */
internal const val IAF_DEEPLINK_ANDROID = "android"

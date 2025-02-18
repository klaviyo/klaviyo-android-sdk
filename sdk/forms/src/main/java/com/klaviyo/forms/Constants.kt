package com.klaviyo.forms

import org.json.JSONArray

/**
 * Fields to replace in the HTML template
 */
internal const val IAF_BRIDGE_NAME = "KlaviyoNativeBridge"
internal const val IAF_BRIDGE_NAME_PLACEHOLDER = "BRIDGE_NAME"
internal const val IAF_SDK_NAME_PLACEHOLDER = "SDK_NAME"
internal const val IAF_SDK_VERSION_PLACEHOLDER = "SDK_VERSION"
internal const val IAF_HANDSHAKE_PLACEHOLDER = "BRIDGE_HANDSHAKE"
internal const val IAF_KLAVIYO_JS_PLACEHOLDER = "KLAVIYO_JS_URL"

/**
 * Decoding top-level message types
 */
internal const val IAF_MESSAGE_DATA_KEY = "data"
internal const val IAF_MESSAGE_TYPE_KEY = "type"
internal const val IAF_TYPE_VERSION_KEY = "version"

internal const val IAF_MESSAGE_TYPE_SHOW = "formWillAppear"
internal const val IAF_MESSAGE_TYPE_CLOSE = "formDisappeared"
internal const val IAF_MESSAGE_TYPE_PROFILE_EVENT = "trackProfileEvent"
internal const val IAF_MESSAGE_TYPE_AGGREGATE_EVENT = "trackAggregateEvent"
internal const val IAF_MESSAGE_TYPE_DEEPLINK = "openDeepLink"
internal const val IAF_MESSAGE_TYPE_ABORT = "abort"
internal const val IAF_MESSAGE_HAND_SHOOK = "handShook"

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
 * Abort fields
 */
internal const val IAF_ABORT_REASON = "reason"

/**
 * Profile event constants
 */
internal const val IAF_METRIC_KEY = "metric"
internal const val IAF_PROPERTIES_KEY = "properties"

/**
 * Deep Link fields
 */
internal const val IAF_DEEPLINK_ANDROID = "android"

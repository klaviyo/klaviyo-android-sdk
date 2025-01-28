package com.klaviyo.messaging

/**
 * Decoding top-level message types
 */
internal const val IAF_MESSAGE_DATA_KEY = "data"
internal const val IAF_MESSAGE_TYPE_KEY = "type"
internal const val IAF_MESSAGE_TYPE_SHOW = "formDidAppear"
internal const val IAF_MESSAGE_TYPE_CLOSE = "formDidClose"
internal const val IAF_MESSAGE_TYPE_PROFILE_EVENT = "profileEventTracked"

/**
 * Profile event constants
 */
internal const val IAF_EVENT_NAME_KEY = "eventName"
internal const val IAF_PROPERTIES_KEY = "properties"

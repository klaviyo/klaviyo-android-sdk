package com.klaviyo.messaging

/**
 * Decoding top-level message types
 */
internal const val IAF_MESSAGE_DATA_KEY = "data"
internal const val IAF_MESSAGE_TYPE_KEY = "type"
internal const val IAF_MESSAGE_TYPE_SHOW = "formDidAppear"
internal const val IAF_MESSAGE_TYPE_CLOSE = "formDidClose"
internal const val IAF_MESSAGE_TYPE_PROFILE_EVENT = "profileEventTracked"
internal const val IAF_MESSAGE_TYPE_AGGREGATE_EVENT = "aggregateEventTracked"

/**
 * Profile event constants
 */
internal const val IAF_METRIC_KEY = "metric"
internal const val IAF_PROPERTIES_KEY = "properties"

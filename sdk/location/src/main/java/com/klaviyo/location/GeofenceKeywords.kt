package com.klaviyo.location

import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric

/**
 * Internal metrics for geofence events
 * These are the event names that will be sent to Klaviyo when geofence transitions occur
 */
internal object GeofenceEventMetric {
    object ENTER : EventMetric.CUSTOM("\$geofence_enter")
    object EXIT : EventMetric.CUSTOM("\$geofence_exit")
    object DWELL : EventMetric.CUSTOM("\$geofence_dwell")
}

/**
 * Internal properties for geofence events
 * These are the property keys that will be included in geofence events
 */
internal object GeofenceEventProperty {
    object GEOFENCE_ID : EventKey.CUSTOM("\$geofence_id")
}

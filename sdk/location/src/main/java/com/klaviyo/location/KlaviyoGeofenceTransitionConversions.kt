package com.klaviyo.location

import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/**
 * Extension function to convert a [GeofencingEvent] to a [KlaviyoGeofenceTransition]
 */
fun GeofencingEvent.toKlaviyoGeofenceEvent(): KlaviyoGeofenceTransition? =
    when (geofenceTransition) {
        Geofence.GEOFENCE_TRANSITION_ENTER -> KlaviyoGeofenceTransition.Entered
        Geofence.GEOFENCE_TRANSITION_EXIT -> KlaviyoGeofenceTransition.Exited
        Geofence.GEOFENCE_TRANSITION_DWELL -> KlaviyoGeofenceTransition.Dwelt
        else -> null
    }

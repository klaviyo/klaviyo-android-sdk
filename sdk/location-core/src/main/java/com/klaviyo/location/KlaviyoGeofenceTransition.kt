package com.klaviyo.location

/**
 * Represents the type of transition that occurred for a geofence event
 * Makes life easier than dealing with the raw integers
 */
enum class KlaviyoGeofenceTransition {
    Entered,
    Exited,
    Dwelt
}

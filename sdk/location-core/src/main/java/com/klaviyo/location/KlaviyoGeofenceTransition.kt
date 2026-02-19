package com.klaviyo.location

import androidx.annotation.RestrictTo

/**
 * Represents the type of transition that occurred for a geofence event.
 * Makes life easier than dealing with the raw integers.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
enum class KlaviyoGeofenceTransition {
    Entered,
    Exited,
    Dwelt
}

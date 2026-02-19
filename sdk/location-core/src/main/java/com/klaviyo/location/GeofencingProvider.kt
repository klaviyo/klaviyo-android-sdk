package com.klaviyo.location

import androidx.annotation.RestrictTo

/**
 * Internal interface for geofence lifecycle management.
 * Implementations handle registering and unregistering geofence monitoring
 * with the underlying platform location services.
 *
 * The `location` module provides [KlaviyoGeofencingProvider] as the concrete implementation,
 * auto-registered via [LocationInitProvider] ContentProvider.
 *
 * @see com.klaviyo.location.Geofencing for the public-facing API
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface GeofencingProvider {
    /**
     * Start geofence monitoring, registering necessary dependencies.
     */
    fun register()

    /**
     * Stop geofence monitoring and clean up resources.
     */
    fun unregister()
}

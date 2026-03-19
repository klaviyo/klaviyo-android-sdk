package com.klaviyo.location

/**
 * Internal interface for geofence lifecycle management.
 * Implementations handle registering and unregistering geofence monitoring
 * with the underlying platform location services.
 *
 * The default implementation is `KlaviyoGeofencingProvider` in the `location` module,
 * auto-registered via a `LocationInitProvider` ContentProvider.
 *
 * @see registerGeofencing
 * @see unregisterGeofencing
 */
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

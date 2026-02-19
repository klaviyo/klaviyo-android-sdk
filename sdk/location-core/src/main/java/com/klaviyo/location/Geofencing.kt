package com.klaviyo.location

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingModule
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply

/**
 * Entrypoint to start geofence monitoring.
 * You should call this as early as possible in your application lifecycle, ideally at app launch.
 * Klaviyo will monitor for permission changes and start/stop geofence monitoring as needed.
 */
fun Klaviyo.registerGeofencing(): Klaviyo = safeApply {
    val provider = Registry.getOrNull<GeofencingProvider>()
        ?: throw MissingModule("location")
    provider.register()
}

/**
 * Stops geofence monitoring.
 * You can call this if you want to stop monitoring geofences, but it's not usually necessary.
 * Klaviyo will automatically stop monitoring if location permissions are revoked.
 */
fun Klaviyo.unregisterGeofencing(): Klaviyo = safeApply {
    val provider = Registry.getOrNull<GeofencingProvider>()
        ?: throw MissingModule("location")
    provider.unregister()
}

/**
 * Java-friendly static methods for Geofencing.
 * Kotlin users should use the extension functions on [Klaviyo] instead.
 */
object KlaviyoLocation {
    /**
     * Start geofence monitoring.
     * Java-friendly static method.
     *
     * @see Klaviyo.registerGeofencing
     */
    @JvmStatic
    fun registerGeofencing() {
        Klaviyo.registerGeofencing()
    }

    /**
     * Stop geofence monitoring.
     * Java-friendly static method.
     *
     * @see Klaviyo.unregisterGeofencing
     */
    @JvmStatic
    fun unregisterGeofencing() {
        Klaviyo.unregisterGeofencing()
    }
}

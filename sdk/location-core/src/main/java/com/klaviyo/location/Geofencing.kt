package com.klaviyo.location

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply

/**
 * Entrypoint to start geofence monitoring.
 * You should call this as early as possible in your application lifecycle, ideally at app launch.
 * Klaviyo will monitor for permission changes and start/stop geofence monitoring as needed.
 *
 * @throws MissingKlaviyoModule if the `com.klaviyo:location` module is not on the classpath.
 */
fun Klaviyo.registerGeofencing(): Klaviyo {
    val provider = Registry.getOrNull<GeofencingProvider>()
        ?: throw MissingKlaviyoModule("location")
    return safeApply { provider.register() }
}

/**
 * Stops geofence monitoring.
 * You can call this if you want to stop monitoring geofences, but it's not usually necessary.
 * Klaviyo will automatically stop monitoring if location permissions are revoked.
 *
 * @throws MissingKlaviyoModule if the `com.klaviyo:location` module is not on the classpath.
 */
fun Klaviyo.unregisterGeofencing(): Klaviyo {
    val provider = Registry.getOrNull<GeofencingProvider>()
        ?: throw MissingKlaviyoModule("location")
    return safeApply { provider.unregister() }
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

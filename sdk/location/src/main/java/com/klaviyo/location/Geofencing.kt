// Using extensions for code organization, even if the receiver parameter is sometimes unused
@file:Suppress("UnusedReceiverParameter")

package com.klaviyo.location

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply
import com.klaviyo.core.safeCall

/**
 * Entrypoint to start geofence monitoring
 * You should call this as early as possible in your application lifecycle, ideally at app launch.
 * Klaviyo will monitor for permission changes and start/stop geofence monitoring as needed.
 */
fun Klaviyo.startGeofenceMonitoring(): Klaviyo = safeApply {
    KlaviyoGeofenceManager.startGeofenceMonitoring()
}

/**
 * Stops geofence monitoring
 * You can call this if you want to stop monitoring geofences, but it's not usually necessary.
 * Klaviyo will automatically stop monitoring if location permissions are revoked.
 */
fun Klaviyo.stopGeofenceMonitoring(): Klaviyo = safeApply {
    KlaviyoGeofenceManager.stopGeofenceMonitoring()
}

/**
 * Register a callback to be invoked when a geofence event occurs.
 *
 * Note: this must be called on Application.onCreate or else events triggered while
 * the app is terminated will not be delivered.
 *
 * TODO Unclear if we should support this at all
 *
 * @param callback The callback function to be invoked with the geofence and transition type
 */
fun Klaviyo.onGeofenceEvent(callback: KlaviyoGeofenceCallback): Klaviyo = safeApply {
    Registry.register<KlaviyoGeofenceCallback>(callback)
}

/**
 * Get the list of currently monitored geofences
 * @return List of KlaviyoGeofence objects, or an empty list if none are being monitored
 */
fun Klaviyo.getGeofences(): List<KlaviyoGeofence> = safeCall {
    KlaviyoGeofenceManager.getGeofences()
} ?: emptyList()

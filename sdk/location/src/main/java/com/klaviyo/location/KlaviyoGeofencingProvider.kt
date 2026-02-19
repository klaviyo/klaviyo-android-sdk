package com.klaviyo.location

import com.klaviyo.core.Registry
import com.klaviyo.core.safeCall

internal class KlaviyoGeofencingProvider : GeofencingProvider {
    override fun register() = safeCall {
        Registry.apply {
            registerOnce<PermissionMonitor> { KlaviyoPermissionMonitor() }
            registerOnce<LocationManager> { KlaviyoLocationManager() }
        }
        Registry.get<LocationManager>().startGeofenceMonitoring()
    } ?: Unit

    override fun unregister() = safeCall {
        Registry.getOrNull<LocationManager>()?.stopGeofenceMonitoring()
            ?: Registry.log.warning("Cannot unregister geofencing, must be registered first.")
    } ?: Unit
}

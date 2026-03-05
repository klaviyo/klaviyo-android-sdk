package com.klaviyo.location

import com.klaviyo.core.Registry

internal class KlaviyoGeofencingProvider : GeofencingProvider {
    override fun register() {
        Registry.apply {
            registerOnce<PermissionMonitor> { KlaviyoPermissionMonitor() }
            registerOnce<LocationManager> { KlaviyoLocationManager() }
        }
        Registry.get<LocationManager>().startGeofenceMonitoring()
    }

    override fun unregister() {
        Registry.getOrNull<LocationManager>()?.stopGeofenceMonitoring()
            ?: Registry.log.warning("Cannot unregister geofencing, must be registered first.")
    }
}

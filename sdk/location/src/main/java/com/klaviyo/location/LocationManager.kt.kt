package com.klaviyo.location

interface LocationManager {
    fun startGeofenceMonitoring()
    fun stopGeofenceMonitoring()
    fun fetchGeofences()
    fun getCurrentGeofences(): List<KlaviyoGeofence>
}

package com.klaviyo.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.klaviyo.core.Registry

class KlaviyoGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Registry.locationManager.handleGeofenceIntent(
                context.applicationContext,
                intent
            )
        } catch (e: Exception) {
            Registry.log.error("Unexpected error handling geofence transition intent", e)
        }
    }
}

package com.klaviyo.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.klaviyo.core.Registry

class KlaviyoGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Registry.locationManager.handleGeofenceIntent(
            context.applicationContext,
            intent,
            goAsync()
        )
    }
}

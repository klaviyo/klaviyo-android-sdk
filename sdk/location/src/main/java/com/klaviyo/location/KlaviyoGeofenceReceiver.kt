package com.klaviyo.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class KlaviyoGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) =
        KlaviyoLocationManager.handleGeofenceIntent(context, intent)
}

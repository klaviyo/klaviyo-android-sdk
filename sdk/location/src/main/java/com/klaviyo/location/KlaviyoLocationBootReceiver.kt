package com.klaviyo.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.klaviyo.core.Registry

/**
 * BroadcastReceiver that handles device boot events to re-register geofences
 * when the device reboots. System geofences are cleared on reboot, so we need
 * to restore them from persistent storage.
 */
class KlaviyoLocationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                Registry.locationManager.restoreGeofencesOnBoot(context)
            } catch (e: Exception) {
                Registry.log.error("Uncaught exception restoring Klaviyo Geofences on boot", e)
            }
        }
    }
}

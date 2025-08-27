package com.klaviyo.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.CheckResult
import androidx.core.app.ActivityCompat
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent

/**
 * Manages location permissions required for geofencing operations.
 *
 * This class provides:
 * - Permission checking for geofencing APIs
 * - Monitoring for permission changes via app lifecycle events
 * - Callbacks when permissions are granted/revoked
 * * Create an instance to start monitoring, dispose/stop to end monitoring.
 */
internal class PermissionMonitor(private val callback: (Boolean) -> Unit) {

    private var lastPermissionState: Boolean

    init {
        lastPermissionState = hasGeofencePermissions()
        callback(lastPermissionState)
        Registry.lifecycleMonitor.onActivityEvent(::onActivityEvent)
        Registry.log.debug("Started monitoring permission changes via lifecycle events")
    }

    /**
     * Stop monitoring for permission changes
     */
    fun dispose() {
        Registry.lifecycleMonitor.offActivityEvent(::onActivityEvent)
        Registry.log.debug("Stopped monitoring permission changes")
    }

    companion object {
        /**
         * Check if we have sufficient permissions for geofencing operations
         */
        @CheckResult
        fun hasGeofencePermissions(context: Context = Registry.config.applicationContext): Boolean =
            hasLocationPermission(context) && hasBackgroundLocationPermission(context)

        /**
         * Get a user-friendly message about which permissions are missing
         */
        fun getMissingPermissionsMessage(context: Context = Registry.config.applicationContext): String? {
            val missingPermissions = mutableListOf<String>()

            if (!hasLocationPermission(context)) {
                missingPermissions.add("Location permission")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission(
                    context
                )
            ) {
                missingPermissions.add("Background location permission")
            }

            return if (missingPermissions.isEmpty()) {
                null
            } else {
                "Missing required permissions: ${missingPermissions.joinToString(", ")}"
            }
        }

        private fun hasLocationPermission(context: Context): Boolean =
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        private fun hasBackgroundLocationPermission(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Not required on older versions
            }
    }

    // Check permissions when app is resumed (user might have changed them in settings)
    private fun onActivityEvent(event: ActivityEvent) {
        if (event is ActivityEvent.Resumed) {
            val currentPermissionState = hasGeofencePermissions()

            // Only notify if permissions actually changed
            if (lastPermissionState != currentPermissionState) {
                Registry.log.debug(
                    "Permission state changed: $lastPermissionState -> $currentPermissionState"
                )
                lastPermissionState = currentPermissionState
                callback(currentPermissionState)
            }
        }
    }
}

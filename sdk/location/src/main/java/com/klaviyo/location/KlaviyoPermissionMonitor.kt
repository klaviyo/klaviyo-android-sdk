package com.klaviyo.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.CheckResult
import androidx.core.app.ActivityCompat
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Extension property to access a shared [PermissionMonitor] instance
 */
internal val Registry.locationPermissionMonitor: PermissionMonitor
    get() = getOrNull<PermissionMonitor>() ?: KlaviyoPermissionMonitor().also {
        register<PermissionMonitor>(it)
    }

/**
 * Manages location permissions required for geofencing operations.
 *
 * This class provides:
 * - Permission checking for geofencing APIs
 * - Monitoring for permission changes via app lifecycle events
 * - Observability of permissions changes
 */
internal class KlaviyoPermissionMonitor() : PermissionMonitor {

    /**
     * Cached permission state to enable change detection
     */
    private var cachedPermissionState: Boolean = try {
        hasGeofencePermissions()
    } catch (_: Exception) {
        false
    }

    override val permissionState: Boolean get() = cachedPermissionState

    private val observers = CopyOnWriteArrayList<PermissionObserver>()

    override fun onPermissionChanged(callback: PermissionObserver) {
        if (observers.isEmpty()) {
            Registry.lifecycleMonitor.onActivityEvent(::onActivityEvent)
        }
        observers += callback
    }

    override fun offPermissionChanged(callback: PermissionObserver) {
        observers -= callback
        if (observers.isEmpty()) {
            Registry.lifecycleMonitor.offActivityEvent(::onActivityEvent)
        }
    }

    private fun notifyObservers(state: Boolean) {
        observers.forEach { it(state) }
    }

    // Check permissions when app is resumed (user might have changed them in settings)
    private fun onActivityEvent(event: ActivityEvent) {
        if (event is ActivityEvent.Resumed) {
            val currentPermissionState = hasGeofencePermissions()
            val previousState = cachedPermissionState

            // Only notify if permissions actually changed
            if (previousState != currentPermissionState) {
                Registry.log.debug(
                    "Permission state changed: $previousState -> $currentPermissionState"
                )
                cachedPermissionState = currentPermissionState
                notifyObservers(currentPermissionState)
            }
        }
    }

    companion object {
        /**
         * Check if we have sufficient permissions for geofencing operations
         */
        @CheckResult
        fun hasGeofencePermissions(context: Context = Registry.config.applicationContext): Boolean =
            hasLocationPermission(context) && hasBackgroundLocationPermission(context)

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
}

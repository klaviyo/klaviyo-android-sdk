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
     * Evaluate on initialize, and updated when permission changes, detected on app resume
     */
    private var cachedPermissionState: Boolean = hasGeofencePermissions()

    /**
     * Public getter to access current permission state on demand
     */
    override val permissionState: Boolean get() = hasGeofencePermissions()

    /**
     * Thread-safe list of observers for permission change events
     */
    private val observers = CopyOnWriteArrayList<PermissionObserver>()

    /**
     * Register an observer to be notified when permission state changes
     *
     * @param unique If true, prevents registering the same observer multiple times.
     *               Note this only works for references e.g. ::method, not lambdas
     * @param callback The observer function to be called when permission state changes
     */
    override fun onPermissionChanged(unique: Boolean, callback: PermissionObserver) {
        if (observers.isEmpty()) {
            // For lower resource usage, monitor lifecycle only if we have a permission observer
            // and make sure cached permission state is up to date when we attach
            Registry.lifecycleMonitor.onActivityEvent(::onActivityEvent)
            cachedPermissionState = hasGeofencePermissions()
        }

        if (!unique || !observers.contains(callback)) {
            observers += callback
        }
    }

    /**
     * Unregister an observer previously added with [onPermissionChanged]
     */
    override fun offPermissionChanged(callback: PermissionObserver) {
        observers -= callback
        if (observers.isEmpty()) {
            Registry.lifecycleMonitor.offActivityEvent(::onActivityEvent)
        }
    }

    /**
     * Broadcast a permission change to registered observers
     */
    private fun notifyObservers(state: Boolean) {
        observers.forEach { it(state) }
    }

    /**
     * Update permission state when app is resumed, and notify observers if it changed
     */
    private fun onActivityEvent(event: ActivityEvent) {
        if (event is ActivityEvent.Resumed) {
            val currentPermissionState = hasGeofencePermissions()

            // Only notify if permissions actually changed
            if (cachedPermissionState != currentPermissionState) {
                Registry.log.debug(
                    "Geofencing permission state changed: $cachedPermissionState -> $currentPermissionState"
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
        fun hasGeofencePermissions(context: Context? = null): Boolean = try {
            val appContext = (context ?: Registry.config.applicationContext)
            hasLocationPermission(appContext) && hasBackgroundLocationPermission(appContext)
        } catch (_: Exception) {
            false
        }

        /**
         * Base location permission [Manifest.permission.ACCESS_FINE_LOCATION] required for geofences
         */
        private fun hasLocationPermission(context: Context): Boolean =
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        /**
         * Background (i.e. "allow all the time") permission is also required on 29+
         */
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

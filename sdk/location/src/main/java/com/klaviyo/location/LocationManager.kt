package com.klaviyo.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

typealias GeofenceObserver = (List<KlaviyoGeofence>) -> Unit

interface LocationManager {
    /**
     * Register an observer to be notified when geofences are synced
     *
     * @param unique If true, prevents registering the same observer multiple times.
     *               Note this only works for references e.g. ::method, not lambdas
     * @param callback The observer function to be called when geofences are synced
     */
    fun onGeofenceSync(unique: Boolean = false, callback: GeofenceObserver)

    /**
     * Unregister an observer previously added with [onGeofenceSync]
     */
    fun offGeofenceSync(callback: GeofenceObserver)

    /**
     * Start monitoring geofences, waiting for necessary permissions if needed
     */
    fun startGeofenceMonitoring()

    /**
     * Stop monitoring all geofences
     */
    fun stopGeofenceMonitoring()

    /**
     * Get the list of currently stored geofences
     */
    fun getStoredGeofences(): List<KlaviyoGeofence>

    /**
     * Handle an incoming geofence intent from the system
     *
     * @param context The application context
     * @param intent The geofence intent from the system
     * @param pendingResult The pending result from goAsync() to be finished when processing completes
     */
    fun handleGeofenceIntent(
        context: Context,
        intent: Intent,
        pendingResult: BroadcastReceiver.PendingResult
    )

    /**
     * Handle device boot event to re-register geofences
     */
    fun restoreGeofencesOnBoot(context: Context)
}

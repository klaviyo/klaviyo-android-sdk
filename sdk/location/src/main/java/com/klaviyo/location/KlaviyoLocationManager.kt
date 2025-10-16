package com.klaviyo.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.klaviyo.core.Registry

/**
 * Coordinator for all geofencing operations
 * - Starts/stops geofence monitoring based on permission state
 * - Fetches geofences from the Klaviyo backend
 * - Adds/removes geofences to/from the system geofencing APIs
 * - Handles geofence transition intents
 * - CHNL-25300 Handle boot receiver events to re-register geofences on device reboot
 */
internal object KlaviyoLocationManager : LocationManager {

    /**
     * Arbitrary int for the pending intent request code
     */
    private const val INTENT_CODE = 23

    /**
     * Lazy-loaded access to the system geofencing APIs
     */
    private val geofencingClient: GeofencingClient by lazy {
        LocationServices.getGeofencingClient(Registry.config.applicationContext)
    }

    /**
     * Pending intent to be used for all our monitored geofences
     */
    private val geofenceIntent by lazy {
        PendingIntent.getBroadcast(
            Registry.config.applicationContext,
            INTENT_CODE,
            Intent(Registry.config.applicationContext, KlaviyoGeofenceReceiver::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun startGeofenceMonitoring() {
        Registry.locationPermissionMonitor.apply {
            onPermissionChanged(::onPermissionChanged)
        }
    }

    override fun stopGeofenceMonitoring() {
        geofencingClient.removeGeofences(geofenceIntent)
        Registry.locationPermissionMonitor.apply {
            offPermissionChanged(::onPermissionChanged)
        }
    }

    @SuppressLint("MissingPermission")
    private fun onPermissionChanged(hasPermissions: Boolean) {
        if (hasPermissions) {
            // Start monitoring geofences
            fetchGeofences()
            monitorGeofences()
        } else {
            // Stop monitoring geofences
            stopGeofenceMonitoring()
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun addGeofence(geofence: KlaviyoGeofence) {
        // TODO CHNL-25306 persist a record of all fences, since system doesn't do it for us
        val geofenceToAdd = Geofence.Builder()
            .setRequestId(geofence.id)
            .setCircularRegion(
                geofence.latitude,
                geofence.longitude,
                geofence.radius
            )
            .setExpirationDuration(-1L)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()

        val geofenceRequest = GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(listOf(geofenceToAdd))
        }.build()

        geofencingClient.addGeofences(geofenceRequest, geofenceIntent).run {
            addOnSuccessListener {
                Registry.log.debug("Added geofence")
            }
            addOnFailureListener {
                Registry.log.error("Failed to add geofence $it")
            }
        }
    }

    private fun removeGeofence(geofence: KlaviyoGeofence) {
        geofencingClient.removeGeofences(listOf(geofence.id))
    }

    // Make the API request -- use coroutines to send it right away
    // attach listener to await the result
    // on success, parse the response into com.klaviyo.location.KlaviyoGeofence objects
    // call saveGeofences with the list of geofences
    override fun fetchGeofences() {
        TODO("CHNL-24475 Fetch geofences from Klaviyo backend and save them locally")
    }

    fun saveGeofences(geofences: List<KlaviyoGeofence>) {
        TODO("CHNL-24471 Save geofences locally and start monitoring them")
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun monitorGeofences() = getCurrentGeofences().forEach { addGeofence(it) }

    override fun getCurrentGeofences(): List<KlaviyoGeofence> {
        // TODO CHNL-24475 return geofences currently monitored
        return emptyList()
    }

    fun handleGeofenceIntent(context: Context?, intent: Intent?) {
        val geofencingEvent = intent?.let { GeofencingEvent.fromIntent(it) } ?: return

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Registry.log.error(errorMessage)
            return
        }

        // Get the transition type.
        val geofenceTransition = geofencingEvent.toKlaviyoGeofenceEvent() ?: run {
            Registry.log.error("Unknown geofence transition ${geofencingEvent.geofenceTransition}")
            return
        }

        geofencingEvent.triggeringGeofences
            ?.map { it.toKlaviyoGeofence() }
            ?.forEach { kGeofence ->
                // CHNL-25308 TODO enqueue API request for geofence event
                // TODO what if the app was terminated, and the host app hasn't called `initialize` yet when we get this intent?
                Registry.log.info("Triggered geofence $geofenceTransition $kGeofence")
            }
    }
}

package com.klaviyo.location

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.klaviyo.core.Registry
import com.klaviyo.location.KlaviyoGeofence.Companion.toKlaviyoGeofence
import com.klaviyo.location.KlaviyoGeofenceTransition.Companion.toKlaviyoGeofenceEvent

internal object KlaviyoGeofenceManager {

    private const val INTENT_CODE = 23

    private var permissionMonitor: PermissionMonitor? = null

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

    fun startGeofenceMonitoring(): KlaviyoGeofenceManager = apply {
        fetchGeofences()

        permissionMonitor = PermissionMonitor { hasPermissions ->
            if (hasPermissions) {
                // Start monitoring geofences
                getGeofences().forEach { addGeofence(it) }
            } else {
                // Stop monitoring geofences
                stopGeofenceMonitoring()
            }
        }
    }

    fun stopGeofenceMonitoring() = apply {
        permissionMonitor?.dispose()?.also { permissionMonitor = null }
        geofencingClient.removeGeofences(geofenceIntent)
    }

    fun addGeofence(geofence: KlaviyoGeofence) {
        // TODO persist a record of all fences, since system doesn't do it for us
        // Boot receiver to re-add them on reboot
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

        if (PermissionMonitor.hasGeofencePermissions()) {
            @Suppress("MissingPermission") // Permission checked by PermissionMonitor.hasGeofencePermissions()
            geofencingClient.addGeofences(geofenceRequest, geofenceIntent).run {
                addOnSuccessListener {
                    Registry.log.debug("Added geofence")
                }
                addOnFailureListener {
                    Registry.log.error("Failed to add geofence $it")
                }
            }
        } else {
            val message = PermissionMonitor.getMissingPermissionsMessage()
            Registry.log.info("Cannot add geofence: $message")
        }
    }

    fun removeGeofence(geofence: KlaviyoGeofence) {
        geofencingClient.removeGeofences(listOf(geofence.id))
    }

    // Make the API request -- use coroutines to send it right away
    // attach listener to await the result
    // on success, parse the response into KlaviyoGeofence objects
    // call saveGeofences with the list of geofences
    fun fetchGeofences() {
        TODO("Fetch geofences from Klaviyo backend and save them locally")
    }

    fun saveGeofences(geofences: List<KlaviyoGeofence>) {
        TODO("Save geofences locally and start monitoring them")
    }

    fun registerGeofences() = getGeofences().forEach { addGeofence(it) }

    fun getGeofences(): List<KlaviyoGeofence> {
        // TODO return geofences currently saved locally
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
                // TODO enqueue API request for geofence event
                // TODO what if the app was terminated, and the host app hasn't called `initialize` yet when we get this intent?
                Registry.log.info("Triggered geofence $geofenceTransition $kGeofence")
            }
    }
}

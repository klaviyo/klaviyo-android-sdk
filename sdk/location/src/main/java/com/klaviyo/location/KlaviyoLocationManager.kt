package com.klaviyo.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER
import com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT
import com.google.android.gms.location.Geofence.NEVER_EXPIRE
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.FetchGeofencesResult
import com.klaviyo.core.Registry
import com.klaviyo.core.safeLaunch
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import org.json.JSONArray

/**
 * Extension property to access a shared [LocationManager] instance
 */
internal val Registry.locationManager: LocationManager
    get() = getOrNull<LocationManager>() ?: KlaviyoLocationManager().also {
        register<LocationManager>(it)
    }

/**
 * Coordinator for all geofencing operations
 * - Starts/stops geofence monitoring based on permission state
 * - Fetches geofences from the Klaviyo backend
 * - Adds/removes geofences to/from the system geofencing APIs
 * - Handles geofence transition intents
 * - TODO CHNL-25300 Handle boot receiver events to re-register geofences on device reboot
 */
internal class KlaviyoLocationManager(
    private val geofencingClient: GeofencingClient? = null,
    private val geofenceIntent: PendingIntent? = null
) : LocationManager {

    companion object {
        /**
         * Arbitrary int for the pending intent request code
         */
        private const val INTENT_CODE = 23

        /**
         * Key for storing geofences in the persistent data store
         */
        private const val GEOFENCES_STORAGE_KEY = "klaviyo_geofences"

        /**
         * Geofence transition types to monitor (enter and exit)
         */
        private const val TRANSITIONS = GEOFENCE_TRANSITION_ENTER or GEOFENCE_TRANSITION_EXIT

        /**
         * Timeout for waiting for broadcast receiver processing to complete
         * We'll use 9.5 seconds (Android docs recommend ~10s)
         */
        private const val BROADCAST_RECEIVER_TIMEOUT = 9_500L
    }

    /**
     * Lazy-loaded access to the system geofencing APIs
     */
    private val client: GeofencingClient by lazy {
        geofencingClient ?: LocationServices.getGeofencingClient(Registry.config.applicationContext)
    }

    /**
     * Pending intent to be used for all our monitored geofences
     */
    private val intent by lazy {
        geofenceIntent ?: PendingIntent.getBroadcast(
            Registry.config.applicationContext,
            INTENT_CODE,
            Intent(Registry.config.applicationContext, KlaviyoGeofenceReceiver::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Thread-safe list of observers for geofence fetch events
     */
    private val observers = CopyOnWriteArrayList<GeofenceObserver>()

    override fun onGeofenceSync(callback: GeofenceObserver) {
        observers += callback
    }

    override fun offGeofenceSync(callback: GeofenceObserver) {
        observers -= callback
    }

    private fun notifyObservers(geofences: List<KlaviyoGeofence>) {
        observers.forEach { observer ->
            observer(geofences)
        }
    }

    /**
     * Start Klaviyo's location monitoring service.
     *
     * If sufficient permission is already granted, then we will fetch geofence data and start
     * monitoring with system's location services immediately
     * If not, we will wait till proper permission is granted before fetching.
     */
    override fun startGeofenceMonitoring() {
        updateSystemMonitoring(Registry.locationPermissionMonitor.permissionState)
        onGeofenceSync(::startSystemMonitoring)
        Registry.locationPermissionMonitor.onPermissionChanged(::updateSystemMonitoring)
    }

    /**
     * Stop Klaviyo's location monitoring service
     * Remove all fences from LocationServices, and stop all listeners
     */
    override fun stopGeofenceMonitoring() {
        stopSystemMonitoring()
        offGeofenceSync(::startSystemMonitoring)
        Registry.locationPermissionMonitor.offPermissionChanged(::updateSystemMonitoring)
    }

    /**
     * Update our system monitoring state based on current permission status
     */
    private fun updateSystemMonitoring(hasPermissions: Boolean) {
        if (hasPermissions) {
            // Start monitoring currently stored geofences
            getStoredGeofences().takeIf { geofences ->
                geofences.isNotEmpty()
            }?.let { geofences ->
                startSystemMonitoring(geofences)
            }

            // Kick off fetch request to refresh geofences from API
            fetchGeofences()
        } else {
            // Only stop if we were previously monitoring
            stopSystemMonitoring()
        }
    }

    /**
     * Fetch geofences from the Klaviyo backend using an immediate API call via coroutines.
     * This bypasses the API queue and makes the request right away.
     * On success, parses the JSON response into KlaviyoGeofence objects and saves them locally.
     */
    fun fetchGeofences() {
        CoroutineScope(Registry.dispatcher).safeLaunch {
            val result = Registry.get<ApiClient>().fetchGeofences()
            when (result) {
                is FetchGeofencesResult.Success -> {
                    result.data.map {
                        it.toKlaviyoGeofence()
                    }.let { geofences ->
                        Registry.log.verbose("Successfully fetched ${geofences.size} geofences")
                        storeGeofences(geofences)
                    }
                }

                is FetchGeofencesResult.Unavailable -> {
                    Registry.log.warning("Geofences are unavailable, device may be offline")
                }

                is FetchGeofencesResult.Failure -> {
                    Registry.log.error("Failed to fetch geofences")
                }
            }
        }
    }

    /**
     * Save geofences to persistent storage
     *
     * @param geofences List of geofences to save
     */
    fun storeGeofences(geofences: List<KlaviyoGeofence>) {
        try {
            // Serialize geofences to JSON array
            val jsonArray = JSONArray().apply {
                geofences.forEach { geofence ->
                    put(geofence.toJson())
                }
            }

            // Store in dataStore
            Registry.dataStore.store(GEOFENCES_STORAGE_KEY, jsonArray.toString())
            Registry.log.verbose("Saved ${geofences.size} geofences to persistent storage")

            // Notify observers that new geofences have been fetched and stored
            notifyObservers(geofences)
        } catch (e: Exception) {
            Registry.log.error("Failed to save geofences to local storage", e)
        }
    }

    /**
     * Remove all geofences from persistent storage
     */
    fun clearStoredGeofences() {
        try {
            Registry.dataStore.clear(GEOFENCES_STORAGE_KEY)
            notifyObservers(emptyList())
            Registry.log.verbose("Cleared all geofences from persistent storage")
        } catch (e: Exception) {
            Registry.log.error("Failed to remove geofences from local storage", e)
        }
    }

    /**
     * Retrieve the list of geofences currently stored in persistent storage
     *
     * @return List of geofences, or empty list if none are stored or parsing fails
     */
    override fun getStoredGeofences(): List<KlaviyoGeofence> = try {
        val geofences = Registry.dataStore.fetch(GEOFENCES_STORAGE_KEY)?.let {
            JSONArray(it).toKlaviyoGeofences()
        } ?: emptyList()

        Registry.log.verbose("Retrieved ${geofences.size} geofences from persistent storage")
        geofences
    } catch (e: Exception) {
        Registry.log.error("Failed to retrieve geofences from storage", e)
        emptyList()
    }

    /**
     * Add the provided geofences with the to system location service's geofencing client.
     */
    @SuppressLint("MissingPermission")
    private fun startSystemMonitoring(geofences: List<KlaviyoGeofence>) {
        // Remove all current geofences from system client first
        stopSystemMonitoring()

        when {
            geofences.isEmpty() -> {
                Registry.log.debug("No geofences to monitor")
                return
            }

            !Registry.locationPermissionMonitor.permissionState -> {
                Registry.log.warning("Insufficient location permission")
                return
            }
        }

        geofences.map { geofence ->
            Geofence.Builder()
                .setRequestId(geofence.id)
                .setCircularRegion(
                    geofence.latitude,
                    geofence.longitude,
                    geofence.radius
                )
                .setExpirationDuration(NEVER_EXPIRE)
                .setTransitionTypes(TRANSITIONS)
                .build()
        }.let { geofencesToAdd ->
            GeofencingRequest.Builder().apply {
                setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                addGeofences(geofencesToAdd)
            }.build()
        }.also { geofenceRequest ->
            client.addGeofences(geofenceRequest, intent).run {
                addOnSuccessListener {
                    Registry.log.debug("Added geofence")
                }
                addOnFailureListener {
                    Registry.log.error("Failed to add geofence $it")
                }
            }
        }
    }

    /**
     * Stop monitoring all Klaviyo geofences
     */
    private fun stopSystemMonitoring() {
        client.removeGeofences(intent)
    }

    /**
     * Handle an incoming geofence event intent from the system
     *
     * Note: Klaviyo may not be initialized yet when this is called, so we handle that case by
     * parsing the company ID from the geofence and initializing Klaviyo automatically.
     */
    override fun handleGeofenceIntent(
        context: Context,
        intent: Intent,
        pendingResult: BroadcastReceiver.PendingResult
    ) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: run {
            Registry.log.warning("Received invalid geofence intent")
            pendingResult.finish()
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Registry.log.error(errorMessage)
            pendingResult.finish()
            return
        }

        val geofenceTransition = geofencingEvent.toKlaviyoGeofenceEvent() ?: run {
            Registry.log.error("Unknown geofence transition ${geofencingEvent.geofenceTransition}")
            pendingResult.finish()
            return
        }

        geofencingEvent.triggeringGeofences
            ?.map { it.toKlaviyoGeofence() }
            ?.forEach { kGeofence ->
                // CHNL-25308 TODO enqueue API request for geofence event
                // TODO what if the app was terminated, and the host app hasn't called `initialize` yet when we get this intent?
                Registry.log.info("Triggered geofence $geofenceTransition $kGeofence")
            }

        pendingResult.finish()
    }
}

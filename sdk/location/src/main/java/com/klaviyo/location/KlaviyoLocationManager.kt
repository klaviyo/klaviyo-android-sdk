package com.klaviyo.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER
import com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT
import com.google.android.gms.location.Geofence.NEVER_EXPIRE
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.ApiObserver
import com.klaviyo.analytics.networking.requests.FetchGeofencesResult
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import java.util.concurrent.CopyOnWriteArrayList
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
     * Start monitoring geofences, waiting for necessary permissions if needed
     */
    override fun startGeofenceMonitoring() {
        // Evaluate initial permission state and start/stop monitoring accordingly
        onPermissionChanged(Registry.locationPermissionMonitor.permissionState)
        onGeofenceSync(::startMonitoring)
        Registry.locationPermissionMonitor.onPermissionChanged(::onPermissionChanged)
    }

    /**
     * Stop monitoring geofences or permission changes
     */
    override fun stopGeofenceMonitoring() {
        stopMonitoring()
        offGeofenceSync(::startMonitoring)
        Registry.locationPermissionMonitor.offPermissionChanged(::onPermissionChanged)
    }

    @SuppressLint("MissingPermission")
    private fun onPermissionChanged(hasPermissions: Boolean) {
        if (hasPermissions) {
            // Start monitoring geofences, and trigger an update from API
            startMonitoring()
            fetchGeofences()
        } else {
            // Only stop if we were previously monitoring
            stopMonitoring()
        }
    }

    /**
     * Fetch geofences from the Klaviyo backend using an immediate API call via coroutines.
     * This bypasses the API queue and makes the request right away.
     * On success, parses the JSON response into KlaviyoGeofence objects and saves them locally.
     */
    fun fetchGeofences() {
        Registry.get<ApiClient>().fetchGeofences { result ->
            when (result) {
                is FetchGeofencesResult.Success -> {
                    // Map FetchedGeofence to KlaviyoGeofence objects using extension function
                    val geofences = result.data.map { it.toKlaviyoGeofence() }

                    Registry.log.verbose("Successfully fetched ${geofences.size} geofences")
                    storeGeofences(geofences)

                    // Notify observers that new geofences have been fetched
                    notifyObservers(geofences)
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
        } catch (e: Exception) {
            Registry.log.error("Failed to save geofences", e)
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
            Registry.log.error("Failed to remove geofences", e)
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
     * Register all currently stored geofences with the system geofencing APIs
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun startMonitoring(geofences: List<KlaviyoGeofence> = getStoredGeofences()) {
        // Remove all current geofences from system client first
        stopMonitoring()

        if (geofences.isEmpty()) {
            Registry.log.debug("No geofences to monitor")
            return
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
    private fun stopMonitoring() {
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

        // Track all API requests we're creating
        val requestUuids = geofencingEvent.triggeringGeofences?.map { it.toKlaviyoGeofence() }
            ?.mapNotNull { kGeofence ->
                if (Registry.getOrNull<State>()?.apiKey == null) {
                    Registry.log.info("Automatically initialized Klaviyo from geofence event")
                    Klaviyo.initialize(kGeofence.companyId, context.applicationContext)
                } else if (Registry.getOrNull<State>()?.apiKey != kGeofence.companyId) {
                    Registry.log.error("Skipping geofence event for non-matching company ID.")
                    null
                }

                Registry.log.info("Triggered geofence $geofenceTransition $kGeofence")
                createGeofenceEvent(geofenceTransition, kGeofence)
            } ?: emptyList()

        // Set up observer and timeout to monitor request completion
        waitForRequestCompletion(requestUuids, pendingResult)
    }

    /**
     * Wait for API requests to complete before finishing the broadcast receiver
     * Uses the API observer pattern to monitor request status
     * Includes timeout safety to ensure pendingResult.finish() is always called
     */
    private fun waitForRequestCompletion(
        requestUuids: List<String>,
        pendingResult: BroadcastReceiver.PendingResult
    ) {
        // If no requests were created, finish immediately
        if (requestUuids.isEmpty()) {
            pendingResult.finish()
            return
        }

        val pendingRequests = requestUuids.toMutableSet()

        // Observer to watch for request completion
        lateinit var observer: ApiObserver
        observer = { request ->
            if (pendingRequests.contains(request.uuid) && request.responseCode is Int) {
                synchronized(pendingRequests) {
                    pendingRequests.remove(request.uuid)

                    if (pendingRequests.isEmpty()) {
                        Registry.get<ApiClient>().offApiRequest(observer)
                        pendingResult.finish()
                    }
                }
            }
        }

        // Register observer
        Registry.get<ApiClient>().onApiRequest(withHistory = false, observer)

        // Safety timeout - ensure we call finish() even if observer doesn't trigger
        Registry.clock.schedule(BROADCAST_RECEIVER_TIMEOUT) {
            synchronized(pendingRequests) {
                if (pendingRequests.isNotEmpty()) {
                    Registry.log.warning(
                        "Geofence requests timed out, ${pendingRequests.size} still pending"
                    )
                }
            }
            Registry.get<ApiClient>().offApiRequest(observer)
            pendingResult.finish()
        }
    }

    /**
     * Creates and enqueues an event for a geofence transition
     *
     * @param transition The type of transition that occurred
     * @param geofence The geofence that triggered the event
     * @return The API request that was enqueued
     */
    private fun createGeofenceEvent(
        transition: KlaviyoGeofenceTransition,
        geofence: KlaviyoGeofence
    ): String? = when (transition) {
        KlaviyoGeofenceTransition.Entered -> GeofenceEventMetric.ENTER
        KlaviyoGeofenceTransition.Exited -> GeofenceEventMetric.EXIT
        KlaviyoGeofenceTransition.Dwelt -> GeofenceEventMetric.DWELL
    }.let { metric ->
        Event(metric, mapOf(GeofenceEventProperty.GEOFENCE_ID to geofence.id))
    }.let { event ->
        Registry.log.debug(
            "Created geofence event: ${event.metric.name} for geofence ${geofence.id}"
        )
        Registry.get<State>().run {
            createEvent(event, getAsProfile())
        }.uniqueId
    }

    /**
     * Handle device boot event to re-register geofences
     *
     * System geofences are cleared on device reboot, so we need to restore them from
     * persistent storage. This method checks for location permissions and re-registers
     * any stored geofences if permissions are granted.
     *
     * Note: This requires Klaviyo to have been initialized at least once before the device reboot
     * so that Config, dataStore, and location permissions are accessible. If Klaviyo has never been
     * initialized, this method will silently return without restoring geofences.
     */
    @SuppressLint("MissingPermission")
    override fun restoreGeofencesOnBoot(context: Context) {
        // Check if we have location permissions
        if (!KlaviyoPermissionMonitor.hasGeofencePermissions(context)) {
            Registry.log.info("Location permissions not granted, skipping geofence restoration")
            return
        }

        Registry.log.info("Handling device boot event to restore geofences")

        if (!Registry.isRegistered<Config>()) {
            // Initialize Klaviyo with config to access dataStore
            Klaviyo.registerForLifecycleCallbacks(context)
        }

        // Re-register geofences with the system
        getStoredGeofences()
            .takeIf { !it.isEmpty() }
            ?.let { storedGeofences ->
                Registry.log.info("Restoring ${storedGeofences.size} geofences after boot")
                startMonitoring(storedGeofences)
            }
            ?: run {
                Registry.log.info("No stored geofences to restore after boot")
            }
    }
}

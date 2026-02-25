package com.klaviyo.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
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
import com.klaviyo.analytics.networking.requests.FetchGeofencesResult
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.core.safeLaunch
import com.klaviyo.location.LocationManager.Companion.MAX_CONCURRENT_GEOFENCES
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await
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
 */
internal class KlaviyoLocationManager : LocationManager {

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
    }

    /**
     * Tracker for managing geofence transition cooldown periods
     */
    private val cooldownTracker by lazy { GeofenceCooldownTracker() }

    /**
     * Lazy-loaded access to the system geofencing APIs
     */
    private val client: GeofencingClient by lazy {
        LocationServices.getGeofencingClient(Registry.config.applicationContext)
    }

    /**
     * Pending intent to be used for all our monitored geofences
     */
    private val intent by lazy {
        PendingIntent.getBroadcast(
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

    /**
     * Observer for company ID changes to refresh geofences
     */
    private val companyObserver by lazy { CompanyObserver() }

    /**
     * Register an observer to be notified when geofences are synced
     *
     * @param unique If true, prevents registering the same observer multiple times.
     *               Note this only works for references e.g. ::method, not lambdas
     * @param callback The observer function to be called when geofences are synced
     */
    override fun onGeofenceSync(unique: Boolean, callback: GeofenceObserver) {
        if (!unique || !observers.contains(callback)) {
            observers += callback
        }
    }

    /**
     * Unregister an observer previously added with [onGeofenceSync]
     */
    override fun offGeofenceSync(callback: GeofenceObserver) {
        observers -= callback
    }

    /**
     * Notify observers when geofences have been updated from API
     */
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
        cooldownTracker.clean()
        updateSystemMonitoring(Registry.locationPermissionMonitor.permissionState)
        onGeofenceSync(true, ::startSystemMonitoringCallback)
        Registry.locationPermissionMonitor.onPermissionChanged(true, ::updateSystemMonitoring)
    }

    /**
     * Stop Klaviyo's location monitoring service
     * Remove all fences from LocationServices, and stop all listeners
     */
    override fun stopGeofenceMonitoring() {
        stopSystemMonitoring()
        companyObserver.stopObserver()
        offGeofenceSync(::startSystemMonitoringCallback)
        Registry.locationPermissionMonitor.offPermissionChanged(::updateSystemMonitoring)
    }

    /**
     * Update our system monitoring state based on current permission status
     */
    private fun updateSystemMonitoring(hasPermissions: Boolean) {
        if (hasPermissions) {
            // Start monitoring currently stored geofences
            Registry.log.info("Required location permissions granted, starting geofence monitoring")
            getStoredGeofences().takeIf { geofences ->
                geofences.isNotEmpty()
            }?.let { geofences ->
                CoroutineScope(Registry.dispatcher).safeLaunch {
                    startSystemMonitoring(geofences)
                }
            }

            // Start observing company ID changes
            companyObserver.startObserver()

            // Kick off fetch request to refresh geofences from API
            fetchGeofences()
        } else {
            Registry.log.debug(
                "Required location permissions not granted, stopping geofence monitoring"
            )
            stopSystemMonitoring()
            companyObserver.stopObserver()
        }
    }

    /**
     * Get the user's current precise location for local geofence sorting.
     * Returns null if location is unavailable or permissions are not granted.
     *
     * This provides the full precision location needed for accurate distance calculations
     * when filtering geofences to the nearest 20.
     *
     * @return Location or null if unavailable
     */
    internal suspend fun getCurrentLocation(): Location? {
        return try {
            // Check if we have location permissions
            if (!Registry.locationPermissionMonitor.permissionState) {
                Registry.log.debug("Location permissions not granted, skipping location fetch")
                return null
            }

            // Get FusedLocationProviderClient
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(
                Registry.config.applicationContext
            )

            // Get last known location
            @SuppressLint("MissingPermission")
            val location = fusedLocationClient.lastLocation.await()

            location?.let {
                Registry.log.verbose(
                    "Got precise location: lat=${it.latitude}, lng=${it.longitude}"
                )
                it
            } ?: run {
                Registry.log.debug("Last known location unavailable")
                null
            }
        } catch (e: CancellationException) {
            // Re-throw to preserve coroutine cancellation
            throw e
        } catch (e: SecurityException) {
            Registry.log.error("Security exception getting location", e)
            null
        } catch (e: Exception) {
            Registry.log.error("Failed to get current location", e)
            null
        }
    }

    /**
     * Get the user's current location, anonymized for privacy.
     * Returns null if location is unavailable or permissions are not granted.
     *
     * The location is anonymized by rounding coordinates to the nearest 0.145 degrees (~10 mile precision).
     * This is done to protect user privacy while still allowing backend filtering.
     *
     * @return AnonymizedLocation or null if unavailable
     */
    private suspend fun getCurrentAnonymizedLocation(): AnonymizedLocation? {
        return getCurrentLocation()?.let { location ->
            val anonymized = AnonymizedLocation.fromLocation(location)
            Registry.log.verbose(
                "Got anonymized location: lat=${anonymized.latitude}, lng=${anonymized.longitude}"
            )
            anonymized
        }
    }

    /**
     * Fetch geofences from the Klaviyo backend using an immediate API call via coroutines.
     * This bypasses the API queue and makes the request right away.
     * On success, parses the JSON response into KlaviyoGeofence objects and saves them locally.
     *
     * Includes the user's anonymized location (if available) to enable backend proximity filtering.
     */
    fun fetchGeofences() {
        CoroutineScope(Registry.dispatcher).safeLaunch {
            // Get anonymized location for backend filtering
            val anonymizedLocation = getCurrentAnonymizedLocation()
            anonymizedLocation?.let {
                Registry.log.debug(
                    "Fetching geofences with anonymized location: lat=${it.latitude}, lng=${it.longitude}"
                )
            } ?: Registry.log.debug("Fetching geofences without location data")

            val result = Registry.get<ApiClient>().fetchGeofences(
                latitude = anonymizedLocation?.latitude,
                longitude = anonymizedLocation?.longitude
            )
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
    override fun clearStoredGeofences() {
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
     * Wrapper for startSystemMonitoring that can be used as a callback.
     * Launches a coroutine to call the suspend function.
     */
    private fun startSystemMonitoringCallback(geofences: List<KlaviyoGeofence>) {
        CoroutineScope(Registry.dispatcher).safeLaunch {
            startSystemMonitoring(geofences)
        }
    }

    /**
     * Add the provided geofences to the system location service's geofencing client.
     * If there are more than 20 geofences, filters to the nearest 20 based on user's current location.
     *
     * @param geofences Full list of geofences (already stored in dataStore)
     */
    @SuppressLint("MissingPermission")
    private suspend fun startSystemMonitoring(geofences: List<KlaviyoGeofence>) {
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

        // Filter to nearest 20 if we have more than 20 geofences
        val geofencesToMonitor = if (geofences.size > MAX_CONCURRENT_GEOFENCES) {
            // Try to get user location for accurate filtering
            @SuppressLint("MissingPermission")
            val location = try {
                LocationServices.getFusedLocationProviderClient(Registry.config.applicationContext)
                    .lastLocation
                    .await()
            } catch (e: CancellationException) {
                // Re-throw to preserve coroutine cancellation
                throw e
            } catch (e: Exception) {
                Registry.log.warning("Failed to get location for filtering", e)
                null
            }

            if (location != null) {
                Registry.log.debug(
                    "Filtering ${geofences.size} geofences to nearest $MAX_CONCURRENT_GEOFENCES based on location"
                )
                GeofenceDistanceCalculator.filterToNearest(
                    geofences,
                    location.latitude,
                    location.longitude,
                    limit = MAX_CONCURRENT_GEOFENCES
                )
            } else {
                Registry.log.warning(
                    "Location unavailable, monitoring first $MAX_CONCURRENT_GEOFENCES of ${geofences.size} geofences"
                )
                geofences.take(MAX_CONCURRENT_GEOFENCES)
            }
        } else {
            geofences
        }

        geofencesToMonitor.map { geofence ->
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
                addGeofences(geofencesToAdd)
                setInitialTrigger(0) // To match iOS behavior, we will NOT treat initial occupancy of a geofence as a triggering event.
            }.build()
        }.also { geofenceRequest ->
            client.addGeofences(geofenceRequest, intent).run {
                addOnSuccessListener {
                    Registry.log.debug(
                        "Monitoring ${geofencesToMonitor.size} geofences with system geofencing client" +
                            if (geofences.size > geofencesToMonitor.size) {
                                " (filtered from ${geofences.size} total)"
                            } else {
                                ""
                            }
                    )
                }
                addOnFailureListener {
                    Registry.log.error("Failed start geofence monitoring: $it")
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
        intent: Intent
    ) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: run {
            Registry.log.warning("Received invalid geofence intent")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Registry.log.error("Received geofence error: $errorMessage")
            return
        }

        val geofenceTransition = geofencingEvent.toKlaviyoGeofenceEvent() ?: run {
            val transition = geofencingEvent.geofenceTransition
            Registry.log.error("Received unknown geofence transition $transition")
            return
        }

        // Create a geofencing event via analytics package for each geofence transition reported
        geofencingEvent.triggeringGeofences?.map { it.toKlaviyoGeofence() }?.forEach { kGeofence ->
            if (Registry.getOrNull<State>()?.apiKey == null) {
                Registry.log.info("Automatically initialized Klaviyo from geofence event")
                Klaviyo.initialize(kGeofence.companyId, context.applicationContext)
            }

            if (Registry.getOrNull<State>()?.apiKey != kGeofence.companyId) {
                // Safeguard against initialization error, or non-matching company ID (this should be impossible though)
                Registry.log.error("Skipping geofence event for non-matching company ID.")
                return@forEach
            }

            // Check cooldown before creating event
            if (!cooldownTracker.isAllowed(kGeofence.id, geofenceTransition)) {
                return@forEach
            }

            Registry.log.info("Triggered geofence $geofenceTransition $kGeofence")

            // Create the event and record the transition time
            cooldownTracker.recordTransition(kGeofence.id, geofenceTransition)
            createGeofenceEvent(geofenceTransition, kGeofence)
        }
    }

    /**
     * Creates and enqueues an event for a geofence transition
     *
     * @param transition The type of transition that occurred
     * @param geofence The geofence that triggered the event
     */
    private fun createGeofenceEvent(
        transition: KlaviyoGeofenceTransition,
        geofence: KlaviyoGeofence
    ) {
        val event = when (transition) {
            KlaviyoGeofenceTransition.Entered -> GeofenceEventMetric.ENTER
            KlaviyoGeofenceTransition.Exited -> GeofenceEventMetric.EXIT
            KlaviyoGeofenceTransition.Dwelt -> GeofenceEventMetric.DWELL
        }.let { metric ->
            Event(metric, mapOf(GeofenceEventProperty.GEOFENCE_ID to geofence.locationId))
        }

        Registry.log.debug("Create geofence event: ${event.metric} for geofence ${geofence.id}")
        Klaviyo.createEvent(event)
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
                CoroutineScope(Registry.dispatcher).safeLaunch {
                    startSystemMonitoring(storedGeofences)
                }
            }
            ?: run {
                Registry.log.info("No stored geofences to restore after boot")
            }
    }
}

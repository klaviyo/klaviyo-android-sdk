package com.klaviyo.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_DWELL
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
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.ApiObserver
import com.klaviyo.analytics.networking.requests.FetchGeofencesResult
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.config.Config
import com.klaviyo.core.safeLaunch
import java.io.Serializable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
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
         * Geofence transition types to monitor (enter, exit, and dwell)
         */
        private const val TRANSITIONS = GEOFENCE_TRANSITION_ENTER or GEOFENCE_TRANSITION_EXIT or GEOFENCE_TRANSITION_DWELL

        /**
         * Timeout for waiting for broadcast receiver processing to complete
         * We'll use 9.5 seconds (Android docs recommend ~10s)
         */
        private const val BROADCAST_RECEIVER_TIMEOUT = 9_500L
    }

    /**
     * Tracker for managing geofence transition cooldown periods
     */
    private val cooldownTracker = GeofenceCooldownTracker()

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
        onGeofenceSync(true, ::startSystemMonitoring)
        Registry.locationPermissionMonitor.onPermissionChanged(true, ::updateSystemMonitoring)
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
            Registry.log.info("Required location permissions granted, starting geofence monitoring")
            getStoredGeofences().takeIf { geofences ->
                geofences.isNotEmpty()
            }?.let { geofences ->
                startSystemMonitoring(geofences)
            }

            // Kick off fetch request to refresh geofences from API
            fetchGeofences()
        } else {
            Registry.log.debug(
                "Required location permissions not granted, stopping geofence monitoring"
            )
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
                .apply {
                    // Set transition types: always include ENTER and EXIT
                    // Only include DWELL if duration is specified (and set loitering delay)
                    val transitions = if (geofence.duration != null) {
                        setLoiteringDelay(geofence.duration * 1000)
                        GEOFENCE_TRANSITION_ENTER or GEOFENCE_TRANSITION_EXIT or GEOFENCE_TRANSITION_DWELL
                    } else {
                        GEOFENCE_TRANSITION_ENTER or GEOFENCE_TRANSITION_EXIT
                    }
                    setTransitionTypes(transitions)
                }
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
                        "Monitoring ${geofences.size} geofences with system geofencing client"
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
            Registry.log.error("Received geofence error: $errorMessage")
            pendingResult.finish()
            return
        }

        val geofenceTransition = geofencingEvent.toKlaviyoGeofenceEvent() ?: run {
            Registry.log.error(
                "Received unknown geofence transition ${geofencingEvent.geofenceTransition}"
            )
            pendingResult.finish()
            return
        }

        // Track the UUIDs of all the API requests we're creating (note, this is typically just 1 request, but can be a handful)
        val requestUuids = geofencingEvent.triggeringGeofences?.map { it.toKlaviyoGeofence() }
            ?.mapNotNull { kGeofence ->
                if (Registry.getOrNull<State>()?.apiKey == null) {
                    Registry.log.info("Automatically initialized Klaviyo from geofence event")
                    Klaviyo.initialize(kGeofence.companyId, context.applicationContext)
                }

                if (Registry.getOrNull<State>()?.apiKey != kGeofence.companyId) {
                    // Safeguard against initialization error, or non-matching company ID (this should be impossible though)
                    Registry.log.error("Skipping geofence event for non-matching company ID.")
                    return@mapNotNull null
                }

                // Check cooldown before creating event
                if (!cooldownTracker.isAllowed(kGeofence.id, geofenceTransition)) {
                    return@mapNotNull null
                }

                Registry.log.info("Triggered geofence $geofenceTransition $kGeofence")

                // Create the event and record the transition time
                cooldownTracker.recordTransition(kGeofence.id, geofenceTransition)
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
        val pendingRequests = requestUuids.toMutableSet()
        val hasFinished = AtomicBoolean(false)
        var observer: ApiObserver? = null
        var timeout: Clock.Cancellable? = null

        // Wrapped finish function, to ensure we can't call it twice (which causes a crash)
        fun finish(observer: ApiObserver? = null, timeout: Clock.Cancellable? = null) {
            if (hasFinished.compareAndSet(false, true)) {
                // Clean up observer/timer
                observer?.let { Registry.get<ApiClient>().offApiRequest(it) }
                timeout?.cancel()

                // And tell broadcast receiver that we've finished
                pendingResult.finish()
            }
        }

        // If no requests were created, finish immediately
        if (pendingRequests.isEmpty()) {
            finish()
            return
        }

        // Timeout - ensure we finish even if network calls don't complete in time
        timeout = Registry.clock.schedule(BROADCAST_RECEIVER_TIMEOUT) {
            val remaining = synchronized(pendingRequests) {
                pendingRequests.size
            }
            if (remaining > 0) {
                Registry.log.warning(
                    "Timeout creating geofence transition events: $remaining remain in queue"
                )
            }
            finish(observer, timeout)
        }

        // As requests complete, remove from pending list, and call finish when all complete
        observer = { request ->
            if (request.responseCode is Int) {
                val remaining = synchronized(pendingRequests) {
                    pendingRequests.remove(request.uuid)
                    pendingRequests.size
                }
                if (remaining == 0) {
                    finish(observer, timeout)
                }
            }
        }

        Registry.get<ApiClient>().onApiRequest(true, observer)
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
        // Build properties map with geofence ID and optional duration
        val properties = mutableMapOf<EventKey, Serializable>(
            GeofenceEventProperty.GEOFENCE_ID to geofence.locationId
        )
        geofence.duration?.let { properties[GeofenceEventProperty.DURATION] = it }
        Event(metric, properties)
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
                startSystemMonitoring(storedGeofences)
            }
            ?: run {
                Registry.log.info("No stored geofences to restore after boot")
            }
    }
}

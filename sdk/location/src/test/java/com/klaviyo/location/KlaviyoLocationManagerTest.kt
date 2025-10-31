package com.klaviyo.location

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.tasks.Task
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.ApiObserver
import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.networking.requests.FetchGeofencesCallback
import com.klaviyo.analytics.networking.requests.FetchGeofencesResult
import com.klaviyo.analytics.networking.requests.FetchedGeofence
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for KlaviyoLocationManager
 *
 * Note: KlaviyoLocationManager is a singleton that manages geofencing operations.
 * This test class focuses on testing the public API and data transformation logic.
 * Some internal state management (like adding/removing geofences) is tested indirectly
 * through integration points, as the module is still in development (WIP).
 */
internal class KlaviyoLocationManagerTest : BaseTest() {

    companion object {
        // NYC coordinates
        const val NYC_LAT = 40.7128
        const val NYC_LON = -74.006
        const val NYC_RADIUS = 100f

        // London coordinates
        const val LONDON_LAT = 51.5074
        const val LONDON_LON = -0.1278
        const val LONDON_RADIUS = 200f
    }

    // Use lazy initialization to access Registry.config.apiKey after setup
    private val NYC: FetchedGeofence
        get() = FetchedGeofence(
            Registry.config.apiKey,
            "location1",
            NYC_LAT,
            NYC_LON,
            NYC_RADIUS.toDouble()
        )

    private val LONDON: FetchedGeofence
        get() = FetchedGeofence(
            Registry.config.apiKey,
            "location2",
            LONDON_LAT,
            LONDON_LON,
            LONDON_RADIUS.toDouble()
        )

    private val mockApiClient = mockk<ApiClient>(relaxed = true)
    private val mockApiRequest = mockk<ApiRequest>(relaxed = true)
    private val mockEvent = mockk<Event>(relaxed = true)

    private val mockAddGeofencesTask = mockk<Task<Void>>(relaxed = true).apply {
        every { addOnSuccessListener(any()) } returns this
        every { addOnFailureListener(any()) } returns this
    }

    private val mockRemoveGeofencesTask = mockk<Task<Void>>(relaxed = true).apply {
        every { addOnSuccessListener(any()) } returns this
        every { addOnFailureListener(any()) } returns this
    }

    private val mockGeofencingClient = mockk<GeofencingClient>(relaxed = true).apply {
        every { addGeofences(any(), any()) } returns mockAddGeofencesTask
        every { removeGeofences(any<PendingIntent>()) } returns mockRemoveGeofencesTask
    }

    private val mockPendingIntent = mockk<PendingIntent>(relaxed = true)

    private val mockPermissionMonitor = mockk<PermissionMonitor>(relaxed = true).apply {
        every { permissionState } returns false
        every { onPermissionChanged(any()) } just runs
        every { offPermissionChanged(any()) } just runs
    }

    private val locationManager = KlaviyoLocationManager(mockGeofencingClient, mockPendingIntent)

    // Mock BroadcastReceiver.PendingResult for geofence intent tests
    private val mockPendingResult = mockk<BroadcastReceiver.PendingResult>(
        relaxed = true
    )

    @Before
    override fun setup() {
        super.setup()

        // Register mocks in the Registry
        Registry.register<ApiClient> { mockApiClient }
        Registry.register<PermissionMonitor> { mockPermissionMonitor }
        Registry.register<LocationManager> { locationManager }
    }

    @After
    override fun cleanup() {
        unmockkStatic(GeofencingEvent::class)
        unmockkObject(KlaviyoPermissionMonitor.Companion)
        unmockkObject(Klaviyo)
        super.cleanup()
    }

    // Helper method to assert geofence properties match expected values
    private fun assertGeofenceEquals(
        geofence: KlaviyoGeofence,
        expectedId: String,
        expectedLatitude: Double,
        expectedLongitude: Double,
        expectedRadius: Float
    ) {
        assertEquals(expectedId, geofence.id)
        assertEquals(expectedLatitude, geofence.latitude, 0.0001)
        assertEquals(expectedLongitude, geofence.longitude, 0.0001)
        assertEquals(expectedRadius, geofence.radius)
    }

    // Helper method to mock successful geofence fetch
    private fun mockSuccessfulFetch(vararg geofences: FetchedGeofence) {
        mockFetchResult(FetchGeofencesResult.Success(geofences.toList()))
    }

    // Helper method to mock fetch with any result
    private fun mockFetchResult(result: FetchGeofencesResult) {
        val callbackSlot = slot<FetchGeofencesCallback>()
        every { mockApiClient.fetchGeofences(capture(callbackSlot)) } answers {
            callbackSlot.captured(result)
            mockApiRequest
        }
    }

    // Helper to capture and invoke permission observer
    private fun capturePermissionObserver(): PermissionObserver {
        val observerSlot = slot<PermissionObserver>()
        verify { mockPermissionMonitor.onPermissionChanged(capture(observerSlot)) }
        return observerSlot.captured
    }

    // Helper to setup monitoring with permissions granted and clear previous geofencing calls
    private fun setupMonitoringWithPermissions() {
        every { mockPermissionMonitor.permissionState } returns true
        locationManager.startGeofenceMonitoring()
        clearMocks(mockGeofencingClient, answers = false)
    }

    // Helper to setup and capture API callback, then invoke it with the given result
    private fun triggerFetchWithResult(result: FetchGeofencesResult) {
        val callbackSlot = slot<FetchGeofencesCallback>()
        every { mockApiClient.fetchGeofences(capture(callbackSlot)) } returns mockApiRequest
        locationManager.fetchGeofences()
        callbackSlot.captured(result)
    }

    // Helper to create geofence JSON for storage tests
    private fun geofenceJson(id: String, lat: Double, lon: Double, radius: Double) = """
        {
          "id": "$id",
          "latitude": $lat,
          "longitude": $lon,
          "radius": $radius
        }
    """.trimIndent()

    // Helper to create JSON array of geofences
    private fun geofencesJson(vararg geofences: String) = """
        [
          ${geofences.joinToString(",\n          ")}
        ]
    """.trimIndent()

    // Helper to setup mock State with API key and profile
    private fun setupMockStateWithApiKey(returnEvent: Event = mockEvent): Triple<String, State, Profile> {
        val apiKey = Registry.config.apiKey
        val mockState = mockk<State>(relaxed = true)
        val mockProfile = mockk<Profile>(relaxed = true)
        every { mockState.apiKey } returns apiKey
        every { mockState.getAsProfile() } returns mockProfile
        every { mockState.createEvent(any(), any()) } returns returnEvent
        Registry.register<State> { mockState }
        return Triple(apiKey, mockState, mockProfile)
    }

    // Helper to create an Event with a specific uniqueId
    private fun createEventWithUuid(uuid: String): Event {
        return mockk<Event>(relaxed = true).apply {
            every { uniqueId } returns uuid
        }
    }

    // Helper to wrap test logic with GeofencingEvent static mocking
    private fun withMockedGeofencingEvent(event: GeofencingEvent, block: () -> Unit) {
        mockkStatic(GeofencingEvent::class)
        try {
            every { GeofencingEvent.fromIntent(any()) } returns event
            block()
        } finally {
            unmockkStatic(GeofencingEvent::class)
        }
    }

    @Test
    fun `locationManager registry extension returns existing instance`() {
        // Pre-register a mock LocationManager
        val mockManager = mockk<LocationManager>()
        Registry.register<LocationManager> { mockManager }

        // Access via registry extension
        val result = Registry.locationManager

        // Should return the existing mock
        assertSame(mockManager, result)
    }

    @Test
    fun `locationManager registry extension creates and registers new instance if none exists`() {
        // Verify no instance exists
        Registry.unregister<LocationManager>()
        assertEquals(null, Registry.getOrNull<LocationManager>())

        // Access via registry extension
        val result = Registry.locationManager

        // Should create and register a new instance
        assertNotNull(result)
        assertTrue(result is KlaviyoLocationManager)
        assertSame(result, Registry.get<LocationManager>())
    }

    @Test
    fun `onGeofencesSynced registers observer`() {
        var callbackInvoked = false
        var receivedGeofences: List<KlaviyoGeofence>? = null

        val observer: GeofenceObserver = { geofences ->
            callbackInvoked = true
            receivedGeofences = geofences
        }

        locationManager.onGeofenceSync(observer)

        // Trigger a fetch that will notify observers
        mockSuccessfulFetch(NYC)
        locationManager.fetchGeofences()

        // Verify observer was called
        assertEquals(true, callbackInvoked)
        assertEquals(1, receivedGeofences?.size)
        assertEquals("${Registry.config.apiKey}:location1", receivedGeofences?.get(0)?.id)
        callbackInvoked = false

        locationManager.clearStoredGeofences()
        assertEquals(true, callbackInvoked)
        assertEquals(0, receivedGeofences?.size)
    }

    @Test
    fun `offGeofencesSynced unregisters observer`() {
        var callbackInvoked = false

        val observer: GeofenceObserver = {
            callbackInvoked = true
        }

        // Register then unregister
        locationManager.onGeofenceSync(observer)
        locationManager.offGeofenceSync(observer)

        // Trigger a fetch
        mockSuccessfulFetch(NYC)
        locationManager.fetchGeofences()

        // Verify observer was NOT called
        assertEquals(false, callbackInvoked)
    }

    @Test
    fun `multiple observers all receive notifications`() {
        var callback1Invoked = false
        var callback2Invoked = false

        val observer1: GeofenceObserver = { callback1Invoked = true }
        val observer2: GeofenceObserver = { callback2Invoked = true }

        locationManager.onGeofenceSync(observer1)
        locationManager.onGeofenceSync(observer2)

        // Trigger a fetch
        mockSuccessfulFetch(NYC)
        locationManager.fetchGeofences()

        // Verify both observers were called
        assertEquals(true, callback1Invoked)
        assertEquals(true, callback2Invoked)
    }

    @Test
    fun `observers are not notified on fetch failure`() {
        var callbackInvoked = false

        val observer: GeofenceObserver = { callbackInvoked = true }
        locationManager.onGeofenceSync(observer)

        // Trigger a fetch that fails
        mockFetchResult(FetchGeofencesResult.Failure)
        locationManager.fetchGeofences()

        // Verify observer was NOT called
        assertEquals(false, callbackInvoked)
    }

    @Test
    fun `getStoredGeofences retrieves persisted geofences from dataStore`() {
        // Manually save geofences to dataStore to test retrieval
        val json = geofencesJson(
            geofenceJson("aPiKeY:geo1", NYC_LAT, NYC_LON, NYC_RADIUS.toDouble()),
            geofenceJson("aPiKeY:geo2", LONDON_LAT, LONDON_LON, LONDON_RADIUS.toDouble())
        )
        Registry.dataStore.store("klaviyo_geofences", json)

        // Retrieve and verify
        val retrieved = locationManager.getStoredGeofences()
        assertEquals(2, retrieved.size)
        assertGeofenceEquals(retrieved[0], "aPiKeY:geo1", NYC_LAT, NYC_LON, NYC_RADIUS)
        assertGeofenceEquals(retrieved[1], "aPiKeY:geo2", LONDON_LAT, LONDON_LON, LONDON_RADIUS)
    }

    @Test
    fun `getStoredGeofences returns empty list when no geofences are stored`() {
        val geofences = locationManager.getStoredGeofences()

        assertEquals(0, geofences.size)
    }

    @Test
    fun `getStoredGeofences handles invalid JSON gracefully`() {
        // Store invalid JSON
        Registry.dataStore.store("klaviyo_geofences", "invalid json")

        // Should return empty list, not crash
        val retrieved = locationManager.getStoredGeofences()
        assertEquals(0, retrieved.size)
    }

    @Test
    fun `getStoredGeofences skips invalid geofence objects`() {
        // Create JSON array with one valid and one invalid geofence
        val json = geofencesJson(
            geofenceJson("valid", 40.0, -74.0, 100.0),
            """
               { "id": "invalid-missing-fields"}
            """.trimIndent()
        )

        Registry.dataStore.store("klaviyo_geofences", json)

        // Should only return the valid geofence
        val retrieved = locationManager.getStoredGeofences()
        assertEquals(1, retrieved.size)
        assertEquals("valid", retrieved[0].id)
    }

    @Test
    fun `storeGeofences saves geofences to dataStore`() {
        val apiKey = Registry.config.apiKey
        val geofences = listOf(
            KlaviyoGeofence("$apiKey:geo1", NYC_LON, NYC_LAT, NYC_RADIUS),
            KlaviyoGeofence("$apiKey:geo2", LONDON_LON, LONDON_LAT, LONDON_RADIUS)
        )

        // Store geofences
        locationManager.storeGeofences(geofences)

        // Retrieve and verify they were stored correctly
        val retrieved = locationManager.getStoredGeofences()
        assertEquals(2, retrieved.size)
        assertGeofenceEquals(retrieved[0], "$apiKey:geo1", NYC_LAT, NYC_LON, NYC_RADIUS)
        assertGeofenceEquals(retrieved[1], "$apiKey:geo2", LONDON_LAT, LONDON_LON, LONDON_RADIUS)
    }

    @Test
    fun `fetchGeofences successfully processes geofence results`() {
        mockSuccessfulFetch(NYC, LONDON)

        // Call fetchGeofences - should not crash
        locationManager.fetchGeofences()

        // Verify the ApiClient was called
        verify { mockApiClient.fetchGeofences(any()) }

        // Verify the geofences were persisted
        val stored = locationManager.getStoredGeofences()
        assertEquals(2, stored.size)
        assertGeofenceEquals(
            stored[0],
            "${Registry.config.apiKey}:location1",
            NYC_LAT,
            NYC_LON,
            NYC_RADIUS
        )
        assertGeofenceEquals(
            stored[1],
            "${Registry.config.apiKey}:location2",
            LONDON_LAT,
            LONDON_LON,
            LONDON_RADIUS
        )
    }

    @Test
    fun `fetchGeofences handles unavailable result without crashing`() {
        mockFetchResult(FetchGeofencesResult.Unavailable)

        // Should not crash when offline
        locationManager.fetchGeofences()

        verify { mockApiClient.fetchGeofences(any()) }
    }

    @Test
    fun `fetchGeofences handles failure result without crashing`() {
        mockFetchResult(FetchGeofencesResult.Failure)

        // Should not crash on failure
        locationManager.fetchGeofences()

        verify { mockApiClient.fetchGeofences(any()) }
    }

    @Test
    fun `clearStoredGeofences clears all geofences from dataStore`() {
        // First, store some geofences
        val json = geofencesJson(
            geofenceJson("geo1", 40.0, -74.0, 100.0)
        )
        Registry.dataStore.store("klaviyo_geofences", json)

        // Verify they're stored
        assertEquals(1, locationManager.getStoredGeofences().size)

        // Remove all geofences
        locationManager.clearStoredGeofences()

        // Verify they're gone
        assertEquals(0, locationManager.getStoredGeofences().size)
    }

    @Test
    fun `startGeofenceMonitoring evaluates initial permission state`() {
        // Set permission monitor to return true (has permissions)
        every { mockPermissionMonitor.permissionState } returns true

        // Start monitoring
        locationManager.startGeofenceMonitoring()

        // Verify permission state was checked and observer was registered
        verify { mockPermissionMonitor.onPermissionChanged(any()) }
        // Verify fetchGeofences was called when permissions are granted
        verify { mockApiClient.fetchGeofences(any()) }
    }

    @Test
    fun `startGeofenceMonitoring registers permission observer`() {
        // Start monitoring
        locationManager.startGeofenceMonitoring()

        // Verify observer was registered
        verify { mockPermissionMonitor.onPermissionChanged(any()) }
    }

    @Test
    fun `startGeofenceMonitoring does not start monitoring when permissions are denied`() {
        // Set permission monitor to return false (no permissions)
        every { mockPermissionMonitor.permissionState } returns false

        // Start monitoring
        locationManager.startGeofenceMonitoring()

        // Verify no geofences were added (since we have no permissions)
        verify(inverse = true) { mockGeofencingClient.addGeofences(any(), any()) }
        verify(inverse = true) { mockApiClient.fetchGeofences(any()) }
    }

    @Test
    fun `stopGeofenceMonitoring unregisters permission observer`() {
        // Start monitoring first
        locationManager.startGeofenceMonitoring()

        // Stop monitoring
        locationManager.stopGeofenceMonitoring()

        // Verify observer was unregistered
        verify { mockPermissionMonitor.offPermissionChanged(any()) }
    }

    @Test
    fun `stopGeofenceMonitoring removes geofences from system`() {
        // Stop monitoring
        locationManager.stopGeofenceMonitoring()

        // Verify geofences were removed
        verify { mockGeofencingClient.removeGeofences(mockPendingIntent) }
    }

    @Test
    fun `permission change to granted triggers monitoring to start`() {
        val apiKey = Registry.config.apiKey
        // Store some geofences first
        locationManager.storeGeofences(
            listOf(
                KlaviyoGeofence("$apiKey:geo1", NYC_LON, NYC_LAT, NYC_RADIUS)
            )
        )

        // Start monitoring (permissions initially denied)
        every { mockPermissionMonitor.permissionState } returns false
        locationManager.startGeofenceMonitoring()

        // Capture and invoke the observer with permission granted
        val observer = capturePermissionObserver()
        observer(true)

        // Verify prior fences are removed
        verify { mockGeofencingClient.removeGeofences(mockPendingIntent) }

        // Verify geofences were added in a single batch call and fetched from API
        verify(exactly = 1) { mockGeofencingClient.addGeofences(any(), any()) }
        verify { mockApiClient.fetchGeofences(any()) }
    }

    @Test
    fun `permission change to denied triggers monitoring to stop`() {
        // Start monitoring (permissions initially granted)
        every { mockPermissionMonitor.permissionState } returns true
        locationManager.startGeofenceMonitoring()

        // Capture and invoke the observer with permission denied
        val observer = capturePermissionObserver()
        observer(false)

        // Verify geofences were removed
        verify { mockGeofencingClient.removeGeofences(any<PendingIntent>()) }
    }

    @Test
    fun `startMonitoring adds all stored geofences to GeofencingClient in one batch`() {
        val apiKey = Registry.config.apiKey
        // Store multiple geofences
        locationManager.storeGeofences(
            listOf(
                KlaviyoGeofence("$apiKey:geo1", NYC_LON, NYC_LAT, NYC_RADIUS),
                KlaviyoGeofence("$apiKey:geo2", LONDON_LON, LONDON_LAT, LONDON_RADIUS)
            )
        )

        // Start monitoring (permissions initially denied)
        every { mockPermissionMonitor.permissionState } returns false
        locationManager.startGeofenceMonitoring()

        // Capture and invoke the observer with permission granted
        val observer = capturePermissionObserver()
        observer(true)

        // Verify addGeofences was called once with a batch of all geofences
        verify(exactly = 1) { mockGeofencingClient.addGeofences(any(), any()) }
    }

    @Test
    fun `full lifecycle - start, permission change, stop`() {
        val apiKey = Registry.config.apiKey
        // Store geofences
        locationManager.storeGeofences(
            listOf(
                KlaviyoGeofence("$apiKey:geo1", NYC_LON, NYC_LAT, NYC_RADIUS)
            )
        )

        // Start monitoring (no permissions initially)
        every { mockPermissionMonitor.permissionState } returns false
        locationManager.startGeofenceMonitoring()
        val observer = capturePermissionObserver()

        // Simulate permission granted
        observer(true)
        verify(exactly = 1) { mockGeofencingClient.addGeofences(any(), any()) }
        verify { mockApiClient.fetchGeofences(any()) }

        // Simulate permission revoked
        observer(false)
        verify(atLeast = 1) { mockGeofencingClient.removeGeofences(any<PendingIntent>()) }

        // Stop monitoring
        locationManager.stopGeofenceMonitoring()
        verify { mockPermissionMonitor.offPermissionChanged(any()) }
    }

    @Test
    fun `fetchGeofences success triggers system monitoring update`() {
        // Setup: Start monitoring with permissions granted
        setupMonitoringWithPermissions()

        // Trigger fetch and simulate successful API response
        triggerFetchWithResult(FetchGeofencesResult.Success(listOf(NYC, LONDON)))

        // Verify geofences were stored
        val stored = locationManager.getStoredGeofences()
        assertEquals(2, stored.size)

        // Verify system monitoring was updated with new geofences in a single batch call
        verify(exactly = 1) { mockGeofencingClient.addGeofences(any(), any()) }
    }

    @Test
    fun `fetchGeofences replaces old geofences in system monitoring`() {
        val apiKey = Registry.config.apiKey
        // Setup: Store initial geofences and start monitoring
        locationManager.storeGeofences(
            listOf(KlaviyoGeofence("$apiKey:old_geo", NYC_LON, NYC_LAT, NYC_RADIUS))
        )

        every { mockPermissionMonitor.permissionState } returns true
        locationManager.startGeofenceMonitoring()

        // Should have added geofences initially in one batch call
        verify(exactly = 1) { mockGeofencingClient.addGeofences(any(), any()) }

        // Clear previous calls and trigger fetch
        clearMocks(mockGeofencingClient, answers = false)
        triggerFetchWithResult(FetchGeofencesResult.Success(listOf(NYC)))

        // Verify stored geofences were replaced
        val stored = locationManager.getStoredGeofences()
        assertEquals(1, stored.size)

        // Verify system monitoring was updated with new geofences in a single batch call
        verify(exactly = 1) {
            mockGeofencingClient.addGeofences(
                match {
                    it.geofences.first().let { geofence ->
                        geofence.latitude == NYC_LAT &&
                            geofence.longitude == NYC_LON &&
                            geofence.radius == NYC_RADIUS
                    } && it.geofences.size == 1
                },
                any()
            )
        }
    }

    @Test
    fun `fetchGeofences failure does not trigger system monitoring update`() {
        // Setup: Start monitoring with permissions granted
        setupMonitoringWithPermissions()

        // Trigger fetch and simulate API failure
        triggerFetchWithResult(FetchGeofencesResult.Failure)

        // Verify system monitoring was NOT updated (no new addGeofences calls)
        verify(exactly = 0) { mockGeofencingClient.addGeofences(any(), any()) }
    }

    @Test
    fun `fetchGeofences unavailable does not trigger system monitoring update`() {
        // Setup: Start monitoring with permissions granted
        setupMonitoringWithPermissions()

        // Trigger fetch and simulate API unavailable (offline)
        triggerFetchWithResult(FetchGeofencesResult.Unavailable)

        // Verify system monitoring was NOT updated (no new addGeofences calls)
        verify(exactly = 0) { mockGeofencingClient.addGeofences(any(), any()) }
    }

    @Test
    fun `handleGeofenceIntent logs warning and finishes when GeofencingEvent is null`() {
        // Mock GeofencingEvent.fromIntent to return null
        mockkStatic(GeofencingEvent::class)
        every { GeofencingEvent.fromIntent(any()) } returns null

        val mockContext = mockk<Context>(relaxed = true)
        val mockIntent = mockk<Intent>(relaxed = true)
        val mockPendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)

        // Call handleGeofenceIntent
        locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

        // Verify warning was logged
        verify { spyLog.warning("Received invalid geofence intent") }

        // Verify pending result was finished
        verify { mockPendingResult.finish() }
    }

    @Test
    fun `handleGeofenceIntent logs error and finishes when GeofencingEvent has error`() {
        // Mock GeofencingEvent with error
        val mockGeofencingEvent = mockk<GeofencingEvent>(relaxed = true)
        every { mockGeofencingEvent.hasError() } returns true
        every { mockGeofencingEvent.errorCode } returns 1000

        mockkStatic(GeofencingEvent::class)
        every { GeofencingEvent.fromIntent(any()) } returns mockGeofencingEvent

        val mockContext = mockk<Context>(relaxed = true)
        val mockIntent = mockk<Intent>(relaxed = true)
        val mockPendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)

        // Call handleGeofenceIntent
        locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

        // Verify error was logged (we can't easily verify the exact message from GeofenceStatusCodes)
        verify { spyLog.error(any<String>()) }

        // Verify pending result was finished
        verify { mockPendingResult.finish() }
    }

    @Test
    fun `handleGeofenceIntent logs error and finishes when geofence transition is unknown`() {
        // Mock GeofencingEvent with unknown transition type
        val mockGeofencingEvent = mockk<GeofencingEvent>(relaxed = true)
        every { mockGeofencingEvent.hasError() } returns false
        every { mockGeofencingEvent.geofenceTransition } returns 999 // Unknown transition

        mockkStatic(GeofencingEvent::class)
        every { GeofencingEvent.fromIntent(any()) } returns mockGeofencingEvent

        val mockContext = mockk<Context>(relaxed = true)
        val mockIntent = mockk<Intent>(relaxed = true)
        val mockPendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)

        // Call handleGeofenceIntent
        locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

        // Verify error was logged with the unknown transition code
        verify { spyLog.error("Unknown geofence transition 999") }

        // Verify pending result was finished
        verify { mockPendingResult.finish() }
    }

    // ========== handleGeofenceIntent Tests ==========

    /**
     * Helper to create a mock GeofencingEvent with specified parameters
     */
    private fun mockGeofencingEvent(
        hasError: Boolean = false,
        errorCode: Int = 0,
        transition: Int = Geofence.GEOFENCE_TRANSITION_ENTER,
        geofences: List<Geofence> = emptyList()
    ): GeofencingEvent = mockk<GeofencingEvent>(relaxed = true).apply {
        every { hasError() } returns hasError
        every { getErrorCode() } returns errorCode
        every { geofenceTransition } returns transition
        every { triggeringGeofences } returns geofences
    }

    /**
     * Helper to create a mock Geofence with specified parameters
     */
    private fun mockGeofence(
        id: String,
        lat: Double = NYC_LAT,
        lon: Double = NYC_LON,
        radius: Float = NYC_RADIUS
    ): Geofence = mockk<Geofence>(relaxed = true).apply {
        every { requestId } returns id
        every { latitude } returns lat
        every { longitude } returns lon
        every { getRadius() } returns radius
    }

    @Test
    fun `handleGeofenceIntent returns early when GeofencingEvent is null`() {
        // Setup: Register State to verify it's not accessed
        val mockState = mockk<State>(relaxed = true)
        Registry.register<State> { mockState }

        // Mock GeofencingEvent.fromIntent to return null
        mockkStatic(GeofencingEvent::class)
        every { GeofencingEvent.fromIntent(any()) } returns null

        val mockIntent = mockk<Intent>(relaxed = true)
        locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

        // Should return early and not create events
        verify(exactly = 0) { mockState.createEvent(any(), any()) }

        unmockkStatic(GeofencingEvent::class)
    }

    @Test
    fun `handleGeofenceIntent logs error and returns when GeofencingEvent has error`() {
        // Setup: Register State to verify it's not accessed
        val mockState = mockk<State>(relaxed = true)
        Registry.register<State> { mockState }

        // Mock GeofencingEvent with error
        val mockEvent = mockGeofencingEvent(hasError = true, errorCode = 1000)
        mockkStatic(GeofencingEvent::class)
        every { GeofencingEvent.fromIntent(any()) } returns mockEvent

        val mockIntent = mockk<Intent>(relaxed = true)
        locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

        // Should log error and return early without creating events
        verify(exactly = 0) { mockState.createEvent(any(), any()) }

        unmockkStatic(GeofencingEvent::class)
    }

    @Test
    fun `handleGeofenceIntent creates event for ENTER transition when State is initialized`() {
        // Setup: Register State in Registry
        val (apiKey, mockState, mockProfile) = setupMockStateWithApiKey()

        // Mock geofence and event
        val geofence = mockGeofence("$apiKey:geo1")
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_ENTER,
            geofences = listOf(geofence)
        )

        withMockedGeofencingEvent(mockEvent) {
            val mockIntent = mockk<Intent>(relaxed = true)
            locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

            // Verify event was created with correct metric
            verify {
                mockState.createEvent(
                    match { event ->
                        event.metric == GeofenceEventMetric.ENTER &&
                            event[GeofenceEventProperty.GEOFENCE_ID] == "$apiKey:geo1"
                    },
                    mockProfile
                )
            }
        }
    }

    @Test
    fun `handleGeofenceIntent creates event for EXIT transition`() {
        // Setup: Register State in Registry
        val (apiKey, mockState, mockProfile) = setupMockStateWithApiKey()

        // Mock geofence and event
        val geofence = mockGeofence("$apiKey:geo1")
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_EXIT,
            geofences = listOf(geofence)
        )

        withMockedGeofencingEvent(mockEvent) {
            val mockIntent = mockk<Intent>(relaxed = true)
            locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

            // Verify event was created with correct metric
            verify {
                mockState.createEvent(
                    match { event ->
                        event.metric == GeofenceEventMetric.EXIT
                    },
                    mockProfile
                )
            }
        }
    }

    @Test
    fun `handleGeofenceIntent creates event for DWELL transition`() {
        // Setup: Register State in Registry
        val (apiKey, mockState, mockProfile) = setupMockStateWithApiKey()

        // Mock geofence and event
        val geofence = mockGeofence("$apiKey:geo1")
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_DWELL,
            geofences = listOf(geofence)
        )

        withMockedGeofencingEvent(mockEvent) {
            val mockIntent = mockk<Intent>(relaxed = true)
            locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

            // Verify event was created with correct metric
            verify {
                mockState.createEvent(
                    match { event ->
                        event.metric == GeofenceEventMetric.DWELL
                    },
                    mockProfile
                )
            }
        }
    }

    @Test
    fun `handleGeofenceIntent creates events for multiple geofences`() {
        // Setup: Register State in Registry
        val (apiKey, mockState, mockProfile) = setupMockStateWithApiKey()

        // Mock multiple geofences
        val geofence1 = mockGeofence("$apiKey:geo1", NYC_LAT, NYC_LON, NYC_RADIUS)
        val geofence2 = mockGeofence("$apiKey:geo2", LONDON_LAT, LONDON_LON, LONDON_RADIUS)
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_ENTER,
            geofences = listOf(geofence1, geofence2)
        )

        withMockedGeofencingEvent(mockEvent) {
            val mockIntent = mockk<Intent>(relaxed = true)
            locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

            // Verify events were created for both geofences
            verify(exactly = 2) {
                mockState.createEvent(any(), mockProfile)
            }
        }
    }

    @Test
    fun `handleGeofenceIntent auto-initializes Klaviyo when State is not registered`() {
        // Setup: Ensure State is NOT in Registry
        Registry.unregister<State>()

        // Mock State that will be registered after initialization
        val mockState = mockk<State>(relaxed = true)
        val mockProfile = mockk<Profile>(relaxed = true)
        every { mockState.getAsProfile() } returns mockProfile
        every { mockState.createEvent(any(), any()) } returns mockEvent

        // Mock Klaviyo.initialize - need to register State when called
        mockkObject(Klaviyo)
        every { Klaviyo.initialize(any(), any<Context>()) } answers {
            Registry.register<State> { mockState }
            Klaviyo
        }

        // Mock geofence with company ID
        val companyId = "TEST_API_KEY"
        val geofence = mockGeofence("$companyId:geo1")
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_ENTER,
            geofences = listOf(geofence)
        )
        mockkStatic(GeofencingEvent::class)
        every { GeofencingEvent.fromIntent(any()) } returns mockEvent

        val mockContext = mockk<Context>(relaxed = true)
        val mockIntent = mockk<Intent>(relaxed = true)
        locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

        // Verify Klaviyo was auto-initialized with correct company ID (using any() for context since applicationContext is called)
        verify { Klaviyo.initialize(companyId, any()) }

        // Verify event was created after initialization
        verify {
            mockState.createEvent(
                match { event -> event.metric == GeofenceEventMetric.ENTER },
                mockProfile
            )
        }

        unmockkStatic(GeofencingEvent::class)
        unmockkObject(Klaviyo)
    }

    @Test
    fun `handleGeofenceIntent logs error for unknown transition type`() {
        // Setup: Register State in Registry
        val mockState = mockk<State>(relaxed = true)
        Registry.register<State> { mockState }

        // Capture API key before using in match block
        val apiKey = Registry.config.apiKey

        // Mock geofence with invalid transition
        val geofence = mockGeofence("$apiKey:geo1")
        val mockEvent = mockGeofencingEvent(
            transition = 999, // Invalid transition
            geofences = listOf(geofence)
        )
        mockkStatic(GeofencingEvent::class)
        every { GeofencingEvent.fromIntent(any()) } returns mockEvent

        val mockIntent = mockk<Intent>(relaxed = true)
        locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

        // Should log error and not create any events
        verify(exactly = 0) { mockState.createEvent(any(), any()) }

        unmockkStatic(GeofencingEvent::class)
    }

    // ========== goAsync() / waitForRequestCompletion Tests ==========

    @Test
    fun `handleGeofenceIntent calls pendingResult finish when request completes successfully`() {
        // Setup: Register State in Registry
        val eventUuid = "test-event-uuid"
        val completableEvent = createEventWithUuid(eventUuid)
        val (apiKey, _, _) = setupMockStateWithApiKey(completableEvent)

        // Create mock request that will be observed
        val completableRequest = mockk<ApiRequest>(relaxed = true).apply {
            every { uuid } returns eventUuid
            every { responseCode } returns 200
        }

        val geofence = mockGeofence("$apiKey:geo1")
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_ENTER,
            geofences = listOf(geofence)
        )

        withMockedGeofencingEvent(mockEvent) {
            val mockIntent = mockk<Intent>(relaxed = true)
            locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

            // Capture the API observer that was registered
            val observerSlot = slot<ApiObserver>()
            verify { mockApiClient.onApiRequest(false, capture(observerSlot)) }

            // Simulate the observer being called with the completed request
            observerSlot.captured(completableRequest)

            // Verify pendingResult.finish() was called
            verify { mockPendingResult.finish() }

            // Verify observer was unregistered
            verify { mockApiClient.offApiRequest(any()) }
        }
    }

    @Test
    fun `handleGeofenceIntent calls pendingResult finish when request fails`() {
        // Setup: Register State in Registry
        val eventUuid = "test-event-uuid"
        val failedEvent = createEventWithUuid(eventUuid)
        val (apiKey, _, _) = setupMockStateWithApiKey(failedEvent)

        // Create mock request that will be observed
        val failedRequest = mockk<ApiRequest>(relaxed = true).apply {
            every { uuid } returns eventUuid
            every { responseCode } returns 500
        }

        val geofence = mockGeofence("$apiKey:geo1")
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_ENTER,
            geofences = listOf(geofence)
        )

        withMockedGeofencingEvent(mockEvent) {
            val mockIntent = mockk<Intent>(relaxed = true)
            locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

            // Capture the API observer that was registered
            val observerSlot = slot<ApiObserver>()
            verify { mockApiClient.onApiRequest(false, capture(observerSlot)) }

            // Simulate the observer being called with the failed request
            observerSlot.captured(failedRequest)

            // Verify pendingResult.finish() was called
            verify { mockPendingResult.finish() }

            // Verify observer was unregistered
            verify { mockApiClient.offApiRequest(any()) }
        }
    }

    @Test
    fun `handleGeofenceIntent calls pendingResult finish on timeout`() {
        // Setup: Register State in Registry
        val eventUuid = "test-event-uuid"
        val pendingEvent = createEventWithUuid(eventUuid)
        val (apiKey, _, _) = setupMockStateWithApiKey(pendingEvent)

        val geofence = mockGeofence("$apiKey:geo1")
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_ENTER,
            geofences = listOf(geofence)
        )

        withMockedGeofencingEvent(mockEvent) {
            val mockIntent = mockk<Intent>(relaxed = true)
            locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

            // Verify timeout was scheduled by checking the scheduled tasks
            assertEquals(1, staticClock.scheduledTasks.size)
            val scheduledTask = staticClock.scheduledTasks.first()
            assertEquals(TIME + 9500L, scheduledTask.time)

            // Execute the timeout task
            scheduledTask.task()

            // Verify pendingResult.finish() was called by timeout
            verify { mockPendingResult.finish() }

            // Verify observer was unregistered
            verify { mockApiClient.offApiRequest(any()) }
        }
    }

    @Test
    fun `handleGeofenceIntent waits for all requests to complete before calling finish`() {
        // Setup: Register State in Registry
        val event1Uuid = "event-1-uuid"
        val event2Uuid = "event-2-uuid"
        val event1 = createEventWithUuid(event1Uuid)
        val event2 = createEventWithUuid(event2Uuid)

        val apiKey = Registry.config.apiKey
        val mockState = mockk<State>(relaxed = true)
        val mockProfile = mockk<Profile>(relaxed = true)
        every { mockState.apiKey } returns apiKey
        every { mockState.getAsProfile() } returns mockProfile
        every { mockState.createEvent(any(), any()) } returnsMany listOf(event1, event2)
        Registry.register<State> { mockState }

        // Create mock requests that will be observed
        val request1 = mockk<ApiRequest>(relaxed = true).apply {
            every { uuid } returns event1Uuid
            every { responseCode } returns 200
        }
        val request2 = mockk<ApiRequest>(relaxed = true).apply {
            every { uuid } returns event2Uuid
            every { responseCode } returns 200
        }

        val geofence1 = mockGeofence("$apiKey:geo1")
        val geofence2 = mockGeofence("$apiKey:geo2")
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_ENTER,
            geofences = listOf(geofence1, geofence2)
        )

        withMockedGeofencingEvent(mockEvent) {
            val mockIntent = mockk<Intent>(relaxed = true)
            locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

            // Capture the API observer that was registered
            val observerSlot = slot<ApiObserver>()
            verify { mockApiClient.onApiRequest(false, capture(observerSlot)) }

            // Simulate only the first request completing
            observerSlot.captured(request1)

            // Verify finish() was NOT called yet (waiting for second request)
            verify(exactly = 0) { mockPendingResult.finish() }

            // Simulate the second request completing
            observerSlot.captured(request2)

            // Now verify finish() was called
            verify(exactly = 1) { mockPendingResult.finish() }

            // Verify observer was unregistered
            verify { mockApiClient.offApiRequest(any()) }
        }
    }

    @Test
    fun `handleGeofenceIntent calls finish immediately when no geofences trigger`() {
        // Setup: Register State in Registry
        val mockState = mockk<State>(relaxed = true)
        Registry.register<State> { mockState }

        // Mock event with NO geofences
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_ENTER,
            geofences = emptyList()
        )
        mockkStatic(GeofencingEvent::class)
        every { GeofencingEvent.fromIntent(any()) } returns mockEvent

        val mockIntent = mockk<Intent>(relaxed = true)
        locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

        // Verify finish() was called immediately
        verify { mockPendingResult.finish() }

        // Verify observer was NOT registered (no requests to wait for)
        verify(exactly = 0) { mockApiClient.onApiRequest(any(), any()) }

        unmockkStatic(GeofencingEvent::class)
    }

    @Test
    fun `handleGeofenceIntent calls finish immediately when triggeringGeofences is null`() {
        // Setup: Register State in Registry
        val mockState = mockk<State>(relaxed = true)
        Registry.register<State> { mockState }

        // Mock event with NULL triggeringGeofences
        val mockEvent = mockk<GeofencingEvent>(relaxed = true).apply {
            every { hasError() } returns false
            every { geofenceTransition } returns Geofence.GEOFENCE_TRANSITION_ENTER
            every { triggeringGeofences } returns null
        }

        mockkStatic(GeofencingEvent::class)
        every { GeofencingEvent.fromIntent(any()) } returns mockEvent

        val mockIntent = mockk<Intent>(relaxed = true)
        locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

        // Verify finish() was called immediately
        verify { mockPendingResult.finish() }

        // Verify observer was NOT registered (no requests to wait for)
        verify(exactly = 0) { mockApiClient.onApiRequest(any(), any()) }

        // Verify no events were created
        verify(exactly = 0) { mockState.createEvent(any(), any()) }

        unmockkStatic(GeofencingEvent::class)
    }

    @Test
    fun `handleGeofenceIntent schedules 9_5 second timeout`() {
        val eventWithUuid = createEventWithUuid("timeout-test-uuid")
        val (apiKey, _, _) = setupMockStateWithApiKey(eventWithUuid)
        val geofence = mockGeofence("$apiKey:geo1")
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_ENTER,
            geofences = listOf(geofence)
        )

        withMockedGeofencingEvent(mockEvent) {
            val mockIntent = mockk<Intent>(relaxed = true)
            locationManager.handleGeofenceIntent(mockContext, mockIntent, mockPendingResult)

            // Verify timeout was scheduled with 9500ms delay by checking the scheduled tasks
            assertEquals(1, staticClock.scheduledTasks.size)
            val scheduledTask = staticClock.scheduledTasks.first()
            assertEquals(TIME + 9500L, scheduledTask.time)
        }
    }

    // --- handleBootEvent tests ---

    @Test
    fun `handleBootEvent returns early when location permissions not granted`() {
        // Mock the companion object method
        mockkObject(KlaviyoPermissionMonitor.Companion)
        every { KlaviyoPermissionMonitor.hasGeofencePermissions(any()) } returns false

        locationManager.restoreGeofencesOnBoot(mockContext)

        // Verify no geofencing operations occurred
        verify(exactly = 0) { mockGeofencingClient.addGeofences(any(), any()) }
    }

    @Test
    fun `handleBootEvent returns early when no geofences are stored`() {
        // Mock the companion object method and Klaviyo initialization
        mockkObject(KlaviyoPermissionMonitor.Companion)
        mockkObject(Klaviyo)
        every { KlaviyoPermissionMonitor.hasGeofencePermissions(any()) } returns true
        every { Klaviyo.registerForLifecycleCallbacks(any()) } returns Klaviyo

        // Ensure no geofences are stored
        locationManager.clearStoredGeofences()

        locationManager.restoreGeofencesOnBoot(mockContext)

        // Verify no geofencing operations occurred
        verify(exactly = 0) { mockGeofencingClient.addGeofences(any(), any()) }
    }

    @Test
    fun `handleBootEvent successfully restores geofences when all conditions met`() {
        val apiKey = Registry.config.apiKey
        // Mock the companion object method and Klaviyo initialization
        mockkObject(KlaviyoPermissionMonitor.Companion)
        mockkObject(Klaviyo)
        every { KlaviyoPermissionMonitor.hasGeofencePermissions(any()) } returns true
        every { Klaviyo.registerForLifecycleCallbacks(any()) } returns Klaviyo

        // Store some geofences
        locationManager.storeGeofences(
            listOf(
                KlaviyoGeofence("$apiKey:geo1", NYC_LON, NYC_LAT, NYC_RADIUS),
                KlaviyoGeofence("$apiKey:geo2", LONDON_LON, LONDON_LAT, LONDON_RADIUS)
            )
        )

        locationManager.restoreGeofencesOnBoot(mockContext)

        // Verify geofences were re-registered with the system
        verify(exactly = 1) { mockGeofencingClient.addGeofences(any(), any()) }
    }

    @Test
    fun `handleBootEvent restores correct number of geofences`() {
        val apiKey = Registry.config.apiKey
        // Mock the companion object method and Klaviyo initialization
        mockkObject(KlaviyoPermissionMonitor.Companion)
        mockkObject(Klaviyo)
        every { KlaviyoPermissionMonitor.hasGeofencePermissions(any()) } returns true
        every { Klaviyo.registerForLifecycleCallbacks(any()) } returns Klaviyo

        // Store 3 geofences
        locationManager.storeGeofences(
            listOf(
                KlaviyoGeofence("$apiKey:geo1", NYC_LON, NYC_LAT, NYC_RADIUS),
                KlaviyoGeofence("$apiKey:geo2", LONDON_LON, LONDON_LAT, LONDON_RADIUS),
                KlaviyoGeofence("$apiKey:geo3", NYC_LON + 1, NYC_LAT + 1, NYC_RADIUS)
            )
        )

        locationManager.restoreGeofencesOnBoot(mockContext)

        // Verify all 3 geofences were added in a single batch
        verify(exactly = 1) {
            mockGeofencingClient.addGeofences(
                match { request ->
                    request.geofences.size == 3
                },
                any()
            )
        }
    }

    @Test
    fun `handleBootEvent restores geofences with correct properties`() {
        val apiKey = Registry.config.apiKey
        // Mock the companion object method and Klaviyo initialization
        mockkObject(KlaviyoPermissionMonitor.Companion)
        mockkObject(Klaviyo)
        every { KlaviyoPermissionMonitor.hasGeofencePermissions(any()) } returns true
        every { Klaviyo.registerForLifecycleCallbacks(any()) } returns Klaviyo

        // Store a single geofence with specific properties
        locationManager.storeGeofences(
            listOf(
                KlaviyoGeofence("$apiKey:test_geo", NYC_LON, NYC_LAT, NYC_RADIUS)
            )
        )

        locationManager.restoreGeofencesOnBoot(mockContext)

        // Verify geofence was added with correct properties
        verify(exactly = 1) {
            mockGeofencingClient.addGeofences(
                match { request ->
                    request.geofences.size == 1 &&
                        request.geofences.first().requestId == "$apiKey:test_geo" &&
                        request.geofences.first().latitude == NYC_LAT &&
                        request.geofences.first().longitude == NYC_LON &&
                        request.geofences.first().radius == NYC_RADIUS
                },
                any()
            )
        }
    }
}

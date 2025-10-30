package com.klaviyo.location

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.tasks.Task
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.networking.requests.FetchGeofencesCallback
import com.klaviyo.analytics.networking.requests.FetchGeofencesResult
import com.klaviyo.analytics.networking.requests.FetchedGeofence
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
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
        val geofences = listOf(
            KlaviyoGeofence("geo1", NYC_LON, NYC_LAT, NYC_RADIUS),
            KlaviyoGeofence("geo2", LONDON_LON, LONDON_LAT, LONDON_RADIUS)
        )

        // Store geofences
        locationManager.storeGeofences(geofences)

        // Retrieve and verify they were stored correctly
        val retrieved = locationManager.getStoredGeofences()
        assertEquals(2, retrieved.size)
        assertGeofenceEquals(retrieved[0], "geo1", NYC_LAT, NYC_LON, NYC_RADIUS)
        assertGeofenceEquals(retrieved[1], "geo2", LONDON_LAT, LONDON_LON, LONDON_RADIUS)
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
        // Store some geofences first
        locationManager.storeGeofences(
            listOf(
                KlaviyoGeofence("geo1", NYC_LON, NYC_LAT, NYC_RADIUS)
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
        // Store multiple geofences
        locationManager.storeGeofences(
            listOf(
                KlaviyoGeofence("geo1", NYC_LON, NYC_LAT, NYC_RADIUS),
                KlaviyoGeofence("geo2", LONDON_LON, LONDON_LAT, LONDON_RADIUS)
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
        // Store geofences
        locationManager.storeGeofences(
            listOf(
                KlaviyoGeofence("geo1", NYC_LON, NYC_LAT, NYC_RADIUS)
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
        // Setup: Store initial geofences and start monitoring
        locationManager.storeGeofences(
            listOf(KlaviyoGeofence("old_geo", NYC_LON, NYC_LAT, NYC_RADIUS))
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
}

package com.klaviyo.location

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.FetchGeofencesResult
import com.klaviyo.analytics.networking.requests.FetchedGeofence
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.MockIntent
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONException
import org.json.JSONObject
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
@OptIn(ExperimentalCoroutinesApi::class)
internal class KlaviyoLocationManagerTest : BaseTest() {

    companion object {
        // NYC coordinates
        const val NYC_LAT = 40.7128
        const val NYC_LNG = -74.006
        const val NYC_RADIUS = 100f

        // London coordinates
        const val LONDON_LAT = 51.5074
        const val LONDON_LNG = -0.1278
        const val LONDON_RADIUS = 200f

        private val stubNYC: FetchedGeofence = FetchedGeofence(
            API_KEY,
            "NYC",
            NYC_LAT,
            NYC_LNG,
            NYC_RADIUS.toDouble()
        )

        private val stubLondon: FetchedGeofence = FetchedGeofence(
            API_KEY,
            "LONDON",
            LONDON_LAT,
            LONDON_LNG,
            LONDON_RADIUS.toDouble()
        )

        private fun FetchedGeofence.toJson() = this.toKlaviyoGeofence().toJson().toString()

        // Helper to create JSON array of geofences
        private fun geofencesJson(vararg geofences: String) = "[${geofences.joinToString(",")}]"
    }

    private val mockApiClient = mockk<ApiClient>(relaxed = true)

    private val mockAddGeofencesTask = mockk<Task<Void>>(relaxed = true).apply {
        every { addOnSuccessListener(any()) } returns this
        every { addOnFailureListener(any()) } returns this
    }

    private val mockRemoveGeofencesTask = mockk<Task<Void>>(relaxed = true).apply {
        every { addOnSuccessListener(any()) } returns this
        every { addOnFailureListener(any()) } returns this
    }

    private val mockGeofencingClient = mockk<GeofencingClient>(relaxed = true).apply {
        mockkStatic(LocationServices::class)
        every { addGeofences(any(), mockPendingIntent) } returns mockAddGeofencesTask
        every { removeGeofences(mockPendingIntent) } returns mockRemoveGeofencesTask
        every { LocationServices.getGeofencingClient(any<Context>()) } returns this
    }

    private val mockPendingIntent = MockIntent.mockPendingIntent()
    private val mockIntent = mockk<Intent>(relaxed = true)

    private val mockPermissionMonitor = mockk<PermissionMonitor>(relaxed = true).apply {
        every { permissionState } returns false
        every { onPermissionChanged(any(), any()) } just runs
        every { offPermissionChanged(any()) } just runs
    }

    private var locationManager = KlaviyoLocationManager()

    private val mockState = mockk<State>(relaxed = true)
    private val mockEvent = mockk<Event>(relaxed = true)
    private val mockProfile = mockk<Profile>(relaxed = true)

    @Before
    override fun setup() {
        super.setup()
        Dispatchers.setMain(dispatcher)

        // Register mocks in the Registry
        Registry.register<ApiClient> { mockApiClient }
        Registry.register<PermissionMonitor> { mockPermissionMonitor }
        Registry.register<LocationManager> { locationManager }
    }

    @After
    override fun cleanup() {
        unmockkStatic(GeofencingEvent::class)
        unmockkStatic(LocationServices::class)
        unmockkStatic(Klaviyo::class)
        MockIntent.unmockPendingIntent()
        unmockkObject(KlaviyoPermissionMonitor.Companion)
        unmockkObject(Klaviyo)
        Dispatchers.resetMain()
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

    private fun mockStoredFences(vararg geofences: FetchedGeofence) {
        // Seed the data store with some geofence JSON
        // Note: this leverages json serialization logic that we test elsewhere!
        locationManager.storeGeofences(
            geofences.map { it.toKlaviyoGeofence() }
        )
    }

    // Helper method to mock successful geofence fetch using coroutines
    private fun mockFetchWithResult(result: FetchGeofencesResult) {
        coEvery { mockApiClient.fetchGeofences() } returns result
        locationManager.fetchGeofences()
        dispatcher.scheduler.advanceUntilIdle()
    }

    // Helper to capture and invoke permission observer
    private fun capturePermissionObserver(): PermissionObserver {
        val observerSlot = slot<PermissionObserver>()
        verify { mockPermissionMonitor.onPermissionChanged(any(), capture(observerSlot)) }
        return observerSlot.captured
    }

    // Helper to setup monitoring with permissions granted and clear previous geofencing calls
    private fun setupMonitoringWithPermissions() {
        every { mockPermissionMonitor.permissionState } returns true
        locationManager.startGeofenceMonitoring()
        clearMocks(mockGeofencingClient, answers = false)
    }

    // Helper to setup mock State with API key and profile
    private fun setupMockStateWithApiKey(returnEvent: Event = mockEvent) {
        every { mockState.apiKey } returns API_KEY
        every { mockState.getAsProfile() } returns mockProfile
        every { mockState.createEvent(any(), any()) } returns returnEvent
        Registry.register<State> { mockState }
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
        lon: Double = NYC_LNG,
        radius: Float = NYC_RADIUS
    ): Geofence = mockk<Geofence>(relaxed = true).apply {
        every { requestId } returns id
        every { latitude } returns lat
        every { longitude } returns lon
        every { getRadius() } returns radius
    }

    // Helper to setup boot event test mocks with permissions granted
    private fun setupBootEventMocks(withPermission: Boolean = true) {
        // mockkStatic is required for @JvmStatic methods
        mockkStatic(Klaviyo::class)
        mockkObject(Klaviyo)
        mockkObject(KlaviyoPermissionMonitor.Companion)
        every { Klaviyo.registerForLifecycleCallbacks(any()) } answers {
            Registry.register<Config> { mockConfig }
            Klaviyo
        }
        every { KlaviyoPermissionMonitor.hasGeofencePermissions(any()) } returns withPermission
        every { mockPermissionMonitor.permissionState } returns withPermission
    }

    // region Registry and Initialization Tests

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

    // endregion

    // region Observer Pattern Tests

    @Test
    fun `onGeofenceSynced registers observer`() {
        var callbackInvoked = false
        var receivedGeofences: List<KlaviyoGeofence>? = null
        val observer: GeofenceObserver = { geofences ->
            callbackInvoked = true
            receivedGeofences = geofences
        }

        locationManager.onGeofenceSync(callback = observer)

        // Trigger a fetch that will notify observers
        mockFetchWithResult(FetchGeofencesResult.Success(listOf(stubNYC)))

        // Verify observer was called
        assertEquals(true, callbackInvoked)
        assertEquals(1, receivedGeofences?.size)
        assertEquals("${Registry.config.apiKey}:NYC", receivedGeofences?.get(0)?.id)
        callbackInvoked = false

        locationManager.clearStoredGeofences()
        assertEquals(true, callbackInvoked)
        assertEquals(0, receivedGeofences?.size)
    }

    @Test
    fun `onGeofenceSync with unique=false allows duplicate observers`() {
        var callCount = 0
        val observer: GeofenceObserver = { callCount++ }

        // Register same observer multiple times with unique=false
        locationManager.onGeofenceSync(false, observer)
        locationManager.onGeofenceSync(false, observer)
        locationManager.onGeofenceSync(false, observer)

        // Trigger notification
        mockFetchWithResult(FetchGeofencesResult.Success(listOf(stubNYC)))

        // Should be called 3 times (once per registration)
        assertEquals(3, callCount)
    }

    @Test
    fun `onGeofenceSync with unique=true prevents duplicate observers`() {
        var callCount = 0
        val observer: GeofenceObserver = { callCount++ }

        // Register same observer multiple times with unique=true
        locationManager.onGeofenceSync(true, observer)
        locationManager.onGeofenceSync(true, observer)
        locationManager.onGeofenceSync(true, observer)

        // Trigger notification
        mockFetchWithResult(FetchGeofencesResult.Success(listOf(stubNYC)))

        // Should be called only once (duplicates were prevented)
        assertEquals(1, callCount)
    }

    @Test
    fun `offGeofencesSynced unregisters observer`() {
        var callbackInvoked = false
        val observer: GeofenceObserver = {
            callbackInvoked = true
        }

        // Register then unregister
        locationManager.onGeofenceSync(callback = observer)
        locationManager.offGeofenceSync(observer)

        // Trigger a fetch
        mockFetchWithResult(FetchGeofencesResult.Success(listOf(stubNYC)))

        // Verify observer was NOT called
        assertEquals(false, callbackInvoked)
    }

    @Test
    fun `multiple observers all receive notifications`() {
        var callback1Invoked = false
        var callback2Invoked = false
        val observer1: GeofenceObserver = { callback1Invoked = true }
        val observer2: GeofenceObserver = { callback2Invoked = true }

        locationManager.onGeofenceSync(callback = observer1)
        locationManager.onGeofenceSync(callback = observer2)

        // Trigger a fetch
        mockFetchWithResult(FetchGeofencesResult.Success(listOf(stubNYC)))

        // Verify both observers were called
        assertEquals(true, callback1Invoked)
        assertEquals(true, callback2Invoked)
    }

    @Test
    fun `observers are not notified on fetch failure`() = runTest {
        var callbackInvoked = false
        val observer: GeofenceObserver = { callbackInvoked = true }

        locationManager.onGeofenceSync(callback = observer)

        // Trigger a fetch that fails
        coEvery { mockApiClient.fetchGeofences() } returns FetchGeofencesResult.Failure
        locationManager.fetchGeofences()
        advanceUntilIdle()

        // Verify observer was NOT called
        assertEquals(false, callbackInvoked)
    }

    // endregion

    // region Persistence Tests

    @Test
    fun `getStoredGeofences retrieves persisted geofences from dataStore`() {
        // Manually save geofences to dataStore to test retrieval
        val json = geofencesJson(
            stubNYC.toJson(),
            stubLondon.toJson()
        )
        Registry.dataStore.store("klaviyo_geofences", json)

        // Retrieve and verify
        val retrieved = locationManager.getStoredGeofences()
        assertEquals(2, retrieved.size)
        assertGeofenceEquals(
            retrieved[0],
            "${Registry.config.apiKey}:NYC",
            NYC_LAT,
            NYC_LNG,
            NYC_RADIUS
        )
        assertGeofenceEquals(
            retrieved[1],
            "${Registry.config.apiKey}:LONDON",
            LONDON_LAT,
            LONDON_LNG,
            LONDON_RADIUS
        )
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
        verify { spyLog.error(any(), any<JSONException>()) }
    }

    @Test
    fun `getStoredGeofences skips invalid geofence objects`() {
        // Create JSON array with one valid and one invalid geofence
        val json = geofencesJson(
            stubNYC.toJson(),
            """{"id": "invalid-missing-fields"}"""
        )

        Registry.dataStore.store("klaviyo_geofences", json)

        // Should only return the valid geofence
        val retrieved = locationManager.getStoredGeofences()
        assertEquals(1, retrieved.size)
        assertEquals("${Registry.config.apiKey}:NYC", retrieved[0].id)
        verify { spyLog.warning(any(), any<JSONException>()) }
    }

    @Test
    fun `storeGeofences saves geofences to dataStore`() {
        // Store geofences
        mockStoredFences(stubNYC, stubLondon)

        // Retrieve and verify they were stored correctly
        val retrieved = locationManager.getStoredGeofences()
        assertEquals(2, retrieved.size)
        assertGeofenceEquals(
            retrieved[0],
            "${Registry.config.apiKey}:NYC",
            NYC_LAT,
            NYC_LNG,
            NYC_RADIUS
        )
        assertGeofenceEquals(
            retrieved[1],
            "${Registry.config.apiKey}:LONDON",
            LONDON_LAT,
            LONDON_LNG,
            LONDON_RADIUS
        )
    }

    // endregion

    // region API Fetch Tests

    @Test
    fun `fetchGeofences successfully processes geofence results`() = runTest {
        mockFetchWithResult(FetchGeofencesResult.Success(listOf(stubNYC, stubLondon)))

        // Verify the ApiClient was called
        coVerify { mockApiClient.fetchGeofences() }

        // Verify the geofences were persisted
        val stored = locationManager.getStoredGeofences()
        assertEquals(2, stored.size)
        assertGeofenceEquals(
            stored[0],
            "${Registry.config.apiKey}:NYC",
            NYC_LAT,
            NYC_LNG,
            NYC_RADIUS
        )
        assertGeofenceEquals(
            stored[1],
            "${Registry.config.apiKey}:LONDON",
            LONDON_LAT,
            LONDON_LNG,
            LONDON_RADIUS
        )
    }

    @Test
    fun `fetchGeofences handles unavailable result without crashing`() = runTest {
        coEvery { mockApiClient.fetchGeofences() } returns FetchGeofencesResult.Unavailable

        // Should not crash when offline
        locationManager.fetchGeofences()
        advanceUntilIdle()

        coVerify { mockApiClient.fetchGeofences() }
    }

    @Test
    fun `fetchGeofences handles failure result without crashing`() = runTest {
        coEvery { mockApiClient.fetchGeofences() } returns FetchGeofencesResult.Failure

        // Should not crash on failure
        locationManager.fetchGeofences()
        advanceUntilIdle()

        coVerify { mockApiClient.fetchGeofences() }
    }

    @Test
    fun `clearStoredGeofences clears all geofences from dataStore and notifies observers`() {
        var callbackInvoked = false
        var geofences: List<KlaviyoGeofence>? = null
        val observer: GeofenceObserver = { receivedGeofences ->
            callbackInvoked = true
            geofences = receivedGeofences
        }
        locationManager.onGeofenceSync(callback = observer)

        // First, store some geofences
        val json = geofencesJson(stubLondon.toJson())
        Registry.dataStore.store("klaviyo_geofences", json)

        // Verify they're stored
        assertEquals(1, locationManager.getStoredGeofences().size)

        // Remove all geofences
        locationManager.clearStoredGeofences()

        // Verify they're gone
        assertEquals(0, locationManager.getStoredGeofences().size)

        // And the callback was invoked correctly
        assertTrue(callbackInvoked)
        assertEquals(0, geofences?.size)
    }

    // endregion

    // region Monitoring Lifecycle Tests

    @Test
    fun `startGeofenceMonitoring evaluates initial permission state`() = runTest {
        // Set permission monitor to return true (has permissions)
        every { mockPermissionMonitor.permissionState } returns true

        // Start monitoring
        locationManager.startGeofenceMonitoring()
        advanceUntilIdle()

        // Verify permission state was checked and observer was registered
        verify { mockPermissionMonitor.onPermissionChanged(true, any()) }
        coVerify(exactly = 1) { mockApiClient.fetchGeofences() }
    }

    @Test
    fun `startGeofenceMonitoring does not start monitoring when permissions are denied`() =
        runTest {
            // Set permission monitor to return false (no permissions)
            every { mockPermissionMonitor.permissionState } returns false

            // Start monitoring
            locationManager.startGeofenceMonitoring()
            advanceUntilIdle()

            // Verify no geofences were added (since we have no permissions)
            verify(inverse = true) { mockGeofencingClient.addGeofences(any(), any()) }
            coVerify(inverse = true) { mockApiClient.fetchGeofences() }
        }

    @Test
    fun `stopGeofenceMonitoring unregisters permission observer and removes geofences from location services`() {
        // Start monitoring first
        locationManager.startGeofenceMonitoring()

        // Stop monitoring
        locationManager.stopGeofenceMonitoring()

        // Verify observer was unregistered
        verify { mockPermissionMonitor.offPermissionChanged(any()) }

        // Verify geofences were removed
        verify { mockGeofencingClient.removeGeofences(mockPendingIntent) }
    }

    @Test
    fun `permission change to granted triggers monitoring to start`() = runTest {
        // Store some geofences first
        mockStoredFences(stubNYC)

        // Start monitoring (permissions initially denied)
        every { mockPermissionMonitor.permissionState } returns false
        locationManager.startGeofenceMonitoring()
        verify(exactly = 1) { mockGeofencingClient.removeGeofences(mockPendingIntent) }
        coVerify(exactly = 0) { mockApiClient.fetchGeofences() }
        verify(exactly = 0) { mockGeofencingClient.addGeofences(any(), any()) }

        // Capture and invoke the observer with permission granted
        val observer = capturePermissionObserver()

        // Update permission state, and notify observer
        every { mockPermissionMonitor.permissionState } returns true
        observer(true)
        advanceUntilIdle()

        // Verify prior fences were removed, new ones synced, and added
        verify(exactly = 2) { mockGeofencingClient.removeGeofences(mockPendingIntent) }
        coVerify(exactly = 1) { mockApiClient.fetchGeofences() }
        verify(exactly = 1) { mockGeofencingClient.addGeofences(any(), any()) }
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
        verify { mockGeofencingClient.removeGeofences(mockPendingIntent) }
    }

    @Test
    fun `startMonitoring adds all stored geofences to GeofencingClient in one batch`() {
        // Store multiple geofences
        mockStoredFences(stubNYC, stubLondon)

        // Start monitoring (permissions initially denied)
        every { mockPermissionMonitor.permissionState } returns false
        locationManager.startGeofenceMonitoring()

        // Capture and invoke the observer with permission granted
        val observer = capturePermissionObserver()

        every { mockPermissionMonitor.permissionState } returns true
        observer(true)

        // Verify addGeofences was called once with a batch of all geofences
        verify(exactly = 1) {
            mockGeofencingClient.addGeofences(
                match { it.geofences.size == 2 },
                mockPendingIntent
            )
        }
    }

    @Test
    fun `full lifecycle - start, permission change, stop`() = runTest {
        // Store geofences
        mockStoredFences(stubNYC, stubLondon)

        // Start monitoring (no permissions initially)
        every { mockPermissionMonitor.permissionState } returns false
        locationManager.startGeofenceMonitoring()
        val observer = capturePermissionObserver()

        // Simulate permission granted
        every { mockPermissionMonitor.permissionState } returns true
        observer(true)
        advanceUntilIdle()
        verify(exactly = 1) { mockGeofencingClient.addGeofences(any(), mockPendingIntent) }
        coVerify { mockApiClient.fetchGeofences() }

        // Simulate permission revoked
        observer(false)
        advanceUntilIdle()
        verify(exactly = 3) { mockGeofencingClient.removeGeofences(mockPendingIntent) }

        // Stop monitoring
        locationManager.stopGeofenceMonitoring()
        verify { mockPermissionMonitor.offPermissionChanged(any()) }
    }

    @Test
    fun `startGeofenceMonitoring is idempotent with observers`() = runTest {
        // Setup: Store some geofences and grant permissions
        mockStoredFences(stubNYC, stubLondon)
        every { mockPermissionMonitor.permissionState } returns true

        // Call startGeofenceMonitoring multiple times
        locationManager.startGeofenceMonitoring()
        locationManager.startGeofenceMonitoring()
        locationManager.startGeofenceMonitoring()
        advanceUntilIdle()

        // Verify that onPermissionChanged was called with unique=true each time
        verify(exactly = 3) { mockPermissionMonitor.onPermissionChanged(true, any()) }

        // Now test that observers don't duplicate by fetching new geofences
        // and verifying system monitoring is updated exactly once
        clearMocks(mockGeofencingClient, answers = false)
        mockFetchWithResult(FetchGeofencesResult.Success(listOf(stubLondon)))

        // Verify system monitoring was updated exactly once
        // (If geofence sync observers were duplicated, these would be called multiple times)
        verify(exactly = 1) { mockGeofencingClient.removeGeofences(mockPendingIntent) }
        verify(exactly = 1) { mockGeofencingClient.addGeofences(any(), mockPendingIntent) }
    }

    @Test
    fun `fetchGeofences success triggers system monitoring update`() = runTest {
        // Setup: Start monitoring with permissions granted
        setupMonitoringWithPermissions()

        // Trigger fetch and simulate successful API response
        mockFetchWithResult(FetchGeofencesResult.Success(listOf(stubNYC, stubLondon)))

        // Verify geofences were stored
        val stored = locationManager.getStoredGeofences()
        assertEquals(2, stored.size)

        // Verify system monitoring was updated with new geofences
        // Note: startMonitoring removes old fences first, then adds new ones
        verify(atLeast = 1) { mockGeofencingClient.removeGeofences(mockPendingIntent) }
        verify(atLeast = 1) { mockGeofencingClient.addGeofences(any(), mockPendingIntent) }
    }

    @Test
    fun `permission change while fetching geofences is caught`() {
        // Setup: Start monitoring with permissions granted
        setupMonitoringWithPermissions()

        // Mimic permission change while fetch was in background
        every { mockPermissionMonitor.permissionState } returns false

        // Trigger fetch and simulate successful API response
        mockFetchWithResult(FetchGeofencesResult.Success(listOf(stubNYC, stubLondon)))

        // Verify permission-protected API was not touched
        verify(exactly = 0) { mockGeofencingClient.addGeofences(any(), any()) }
    }

    @Test
    fun `fetchGeofences replaces old geofences in system monitoring`() = runTest {
        // Setup: Store initial geofences and start monitoring
        mockStoredFences(stubNYC)

        every { mockPermissionMonitor.permissionState } returns true
        locationManager.startGeofenceMonitoring()

        // Should have added geofences initially
        verify(atLeast = 1) { mockGeofencingClient.addGeofences(any(), mockPendingIntent) }

        // Clear previous calls and trigger fetch
        clearMocks(mockGeofencingClient, answers = false)
        mockFetchWithResult(FetchGeofencesResult.Success(listOf(stubNYC)))

        // Verify stored geofences were replaced
        val stored = locationManager.getStoredGeofences()
        assertEquals(1, stored.size)

        // Verify system monitoring was updated with new geofences
        // Note: startMonitoring removes old fences first, then adds new ones
        verify(atLeast = 1) {
            mockGeofencingClient.addGeofences(
                match {
                    it.geofences.first().let { geofence ->
                        geofence.latitude == NYC_LAT &&
                            geofence.longitude == NYC_LNG &&
                            geofence.radius == NYC_RADIUS
                    } && it.geofences.size == 1
                },
                mockPendingIntent
            )
        }
    }

    @Test
    fun `fetchGeofences failure does not trigger system monitoring update`() = runTest {
        // Setup: Start monitoring with permissions granted
        setupMonitoringWithPermissions()

        // Trigger fetch and simulate API failure
        mockFetchWithResult(FetchGeofencesResult.Failure)

        // Verify system monitoring was NOT updated (no new addGeofences calls)
        verify(exactly = 0) { mockGeofencingClient.addGeofences(any(), any()) }
    }

    @Test
    fun `fetchGeofences unavailable does not trigger system monitoring update`() = runTest {
        // Setup: Start monitoring with permissions granted
        setupMonitoringWithPermissions()

        // Trigger fetch and simulate API unavailable (offline)
        mockFetchWithResult(FetchGeofencesResult.Unavailable)

        // Verify system monitoring was NOT updated (no new addGeofences calls)
        verify(exactly = 0) { mockGeofencingClient.addGeofences(any(), any()) }
    }

    // endregion

    // region Intent Handling Tests

    @Test
    fun `handleGeofenceIntent logs warning when GeofencingEvent is null`() {
        // Mock GeofencingEvent.fromIntent to return null
        mockkStatic(GeofencingEvent::class)
        every { GeofencingEvent.fromIntent(any()) } returns null

        // Call handleGeofenceIntent
        locationManager.handleGeofenceIntent(mockContext, mockIntent)

        // Verify warning was logged
        verify { spyLog.warning(any()) }
    }

    @Test
    fun `handleGeofenceIntent logs error when GeofencingEvent has error`() {
        // Mock GeofencingEvent with error
        val mockGeofencingEvent = mockGeofencingEvent(hasError = true, errorCode = 1000)
        mockkStatic(GeofencingEvent::class)
        every { GeofencingEvent.fromIntent(any()) } returns mockGeofencingEvent

        // Call handleGeofenceIntent
        locationManager.handleGeofenceIntent(mockContext, mockIntent)

        // Verify error was logged (we can't easily verify the exact message from GeofenceStatusCodes)
        verify { spyLog.error(any<String>()) }
    }

    @Test
    fun `handleGeofenceIntent logs error when geofence transition is unknown`() {
        // Mock GeofencingEvent with unknown transition type
        val mockGeofencingEvent = mockGeofencingEvent(transition = 999)
        mockkStatic(GeofencingEvent::class)
        every { GeofencingEvent.fromIntent(any()) } returns mockGeofencingEvent

        // Call handleGeofenceIntent
        locationManager.handleGeofenceIntent(mockContext, mockIntent)

        // Verify error was logged with the unknown transition code
        verify { spyLog.error(match { it.contains("999") }) }
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
        locationManager.handleGeofenceIntent(mockContext, mockIntent)

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
        locationManager.handleGeofenceIntent(mockContext, mockIntent)

        // Should log error and return early without creating events
        verify(exactly = 0) { mockState.createEvent(any(), any()) }

        unmockkStatic(GeofencingEvent::class)
    }

    @Test
    fun `handleGeofenceIntent creates event for ENTER transition when State is initialized`() {
        // Setup: Register State in Registry
        setupMockStateWithApiKey()

        // Mock geofence and event
        val geofence = mockGeofence("$API_KEY:geo1")
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_ENTER,
            geofences = listOf(geofence)
        )

        withMockedGeofencingEvent(mockEvent) {
            val mockIntent = mockk<Intent>(relaxed = true)
            locationManager.handleGeofenceIntent(mockContext, mockIntent)

            // Verify event was created with correct metric
            verify {
                mockState.createEvent(
                    match { event ->
                        event.metric == GeofenceEventMetric.ENTER &&
                            event[GeofenceEventProperty.GEOFENCE_ID] == "geo1"
                    },
                    mockProfile
                )
            }
        }
    }

    @Test
    fun `handleGeofenceIntent creates event for EXIT transition`() {
        // Setup: Register State in Registry
        setupMockStateWithApiKey()

        // Mock geofence and event
        val geofence = mockGeofence("$API_KEY:geo1")
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_EXIT,
            geofences = listOf(geofence)
        )

        withMockedGeofencingEvent(mockEvent) {
            val mockIntent = mockk<Intent>(relaxed = true)
            locationManager.handleGeofenceIntent(mockContext, mockIntent)

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
    fun `handleGeofenceIntent creates events for multiple geofences`() {
        // Setup: Register State in Registry
        setupMockStateWithApiKey()

        // Mock multiple geofences
        val geofence1 = mockGeofence("$API_KEY:geo1", NYC_LAT, NYC_LNG, NYC_RADIUS)
        val geofence2 = mockGeofence("$API_KEY:geo2", LONDON_LAT, LONDON_LNG, LONDON_RADIUS)
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_ENTER,
            geofences = listOf(geofence1, geofence2)
        )

        withMockedGeofencingEvent(mockEvent) {
            val mockIntent = mockk<Intent>(relaxed = true)
            locationManager.handleGeofenceIntent(mockContext, mockIntent)

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
        // mockkStatic is required for @JvmStatic methods
        mockkStatic(Klaviyo::class)
        mockkObject(Klaviyo)
        every { Klaviyo.initialize(any(), any<Context>()) } answers {
            Registry.register<State> { mockState }
            every { mockState.apiKey } returns API_KEY
            Klaviyo
        }

        // Mock geofence with company ID
        val geofence1 = mockGeofence("$API_KEY:geo1")
        val geofence2 = mockGeofence("abc123:geo2")
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_ENTER,
            geofences = listOf(geofence1, geofence2)
        )
        mockkStatic(GeofencingEvent::class)
        every { GeofencingEvent.fromIntent(any()) } returns mockEvent

        val mockContext = mockk<Context>(relaxed = true)
        val mockIntent = mockk<Intent>(relaxed = true)
        locationManager.handleGeofenceIntent(mockContext, mockIntent)

        // Verify Klaviyo was auto-initialized with correct company ID (using any() for context since applicationContext is called)
        verify { Klaviyo.initialize(API_KEY, any()) }

        // Verify event was created after initialization
        verify {
            mockState.createEvent(
                match { event -> event.metric == GeofenceEventMetric.ENTER },
                mockProfile
            )
        }

        verify(exactly = 1) { mockState.createEvent(any(), any()) }
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
        locationManager.handleGeofenceIntent(mockContext, mockIntent)

        // Should log error and not create any events
        verify(exactly = 0) { mockState.createEvent(any(), any()) }

        unmockkStatic(GeofencingEvent::class)
    }

    @Test
    fun `handleGeofenceIntent suppresses duplicate event within cooldown period`() {
        // Setup: Register State in Registry
        setupMockStateWithApiKey()

        val geofenceId = "$API_KEY:geo1"
        val geofence = mockGeofence(geofenceId)

        // Store a recent transition timestamp (30 seconds ago) in the map
        val cooldownMap = JSONObject().apply {
            put("$geofenceId:Entered", TIME - 30_000)
        }
        Registry.dataStore.store("geofence_cooldowns", cooldownMap.toString())

        // Mock geofence event (same geofence, same transition)
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_ENTER,
            geofences = listOf(geofence)
        )

        withMockedGeofencingEvent(mockEvent) {
            val mockIntent = mockk<Intent>(relaxed = true)
            locationManager.handleGeofenceIntent(mockContext, mockIntent)

            // Verify event was NOT created (within cooldown)
            verify(exactly = 0) { mockState.createEvent(any(), any()) }

            // Verify verbose log was called for suppression
            verify {
                spyLog.debug(
                    match { it.contains("Suppressed") && it.contains("30s remaining") }
                )
            }
        }
    }

    @Test
    fun `handleGeofenceIntent creates event after cooldown period expires`() {
        // Setup: Register State in Registry
        setupMockStateWithApiKey()

        val geofenceId = "$API_KEY:geo1"
        val geofence = mockGeofence(geofenceId)

        // Store an old transition timestamp (70 seconds ago, beyond 60s cooldown)
        val cooldownMap = JSONObject().apply {
            put("$geofenceId:Entered", TIME - 70_000)
        }
        Registry.dataStore.store("geofence_cooldowns", cooldownMap.toString())

        // Mock geofence event (same geofence, same transition)
        val mockEvent = mockGeofencingEvent(
            transition = Geofence.GEOFENCE_TRANSITION_ENTER,
            geofences = listOf(geofence)
        )

        withMockedGeofencingEvent(mockEvent) {
            val mockIntent = mockk<Intent>(relaxed = true)
            locationManager.handleGeofenceIntent(mockContext, mockIntent)

            // Verify event WAS created (cooldown expired)
            verify { mockState.createEvent(any(), any()) }

            // Verify new timestamp was stored
            val storedJson = Registry.dataStore.fetch("geofence_cooldowns")
            assertNotNull(storedJson)
            val updatedMap = JSONObject(storedJson)
            assertEquals(TIME, updatedMap.getLong("$geofenceId:Entered"))
        }
    }

    // endregion

    // region Boot receiver tests

    @Test
    fun `handleBootEvent returns early when location permissions not granted`() {
        setupBootEventMocks(false)

        locationManager.restoreGeofencesOnBoot(mockContext)

        // Verify no geofencing operations occurred
        verify(exactly = 0) { mockGeofencingClient.addGeofences(any(), any()) }
    }

    @Test
    fun `handleBootEvent returns early when no geofences are stored`() {
        setupBootEventMocks()

        // Ensure no geofences are stored
        locationManager.clearStoredGeofences()

        locationManager.restoreGeofencesOnBoot(mockContext)

        // Verify no geofencing operations occurred
        verify(exactly = 0) { mockGeofencingClient.addGeofences(any(), any()) }
    }

    @Test
    fun `handleBootEvent successfully restores geofences when all conditions met`() {
        mockStoredFences(stubNYC, stubLondon)
        setupBootEventMocks()

        locationManager.restoreGeofencesOnBoot(mockContext)

        // Verify geofences were re-registered with the system
        verify(exactly = 1) { mockGeofencingClient.addGeofences(any(), any()) }
    }

    @Test
    fun `handleBootEvent restores correct number of geofences`() {
        mockStoredFences(stubNYC, stubLondon)
        setupBootEventMocks()

        locationManager.restoreGeofencesOnBoot(mockContext)

        // Verify all geofences were added in a single batch
        verify(exactly = 1) {
            mockGeofencingClient.addGeofences(
                match { request ->
                    request.geofences.size == 2
                },
                any()
            )
        }
    }

    @Test
    fun `handleBootEvent restores geofences with correct properties`() {
        mockStoredFences(stubNYC)
        setupBootEventMocks()

        locationManager.restoreGeofencesOnBoot(mockContext)

        // Verify geofence was added with correct properties
        verify(exactly = 1) {
            mockGeofencingClient.addGeofences(
                match { request ->
                    request.geofences.size == 1 &&
                        request.geofences.first().requestId == "$API_KEY:${stubNYC.id}" &&
                        request.geofences.first().latitude == NYC_LAT &&
                        request.geofences.first().longitude == NYC_LNG &&
                        request.geofences.first().radius == NYC_RADIUS
                },
                any()
            )
        }
    }

    @Test
    fun `handleBootEvent initializes SDK from cold launch when Config not registered`() {
        // Seed the data store fixture with some mock fence JSON
        mockStoredFences(stubNYC, stubLondon)

        // Set up an environment where klaviyo is not yet initialized/configured
        Registry.unregister<Config>()
        setupBootEventMocks()
        MockIntent.mockPendingIntent()

        // Mock static methods that are called during lazy initialization
        mockkStatic(LocationServices::class)
        every { LocationServices.getGeofencingClient(any<Context>()) } returns mockGeofencingClient

        // Create a fresh LocationManager instance without constructor injection
        // This will trigger lazy initialization when geofences are restored
        val coldLaunchManager = KlaviyoLocationManager()
        coldLaunchManager.restoreGeofencesOnBoot(mockContext)

        // Verify SDK initialization was called FIRST to set up Config
        verify(exactly = 1) { Klaviyo.registerForLifecycleCallbacks(mockContext) }

        // Verify lazy initialization accessed Registry.config.applicationContext AFTER initialization
        verify(exactly = 1) { LocationServices.getGeofencingClient(mockContext) }
        verify(exactly = 1) {
            PendingIntent.getBroadcast(
                any(),
                any(),
                any(),
                any()
            )
        }

        // Verify geofences were still restored after initialization
        verify(exactly = 1) {
            mockGeofencingClient.addGeofences(
                match { request ->
                    request.geofences.size == 2
                },
                any()
            )
        }

        // Cleanup static mocks
        unmockkStatic(LocationServices::class)
        unmockkStatic(PendingIntent::class)
        unmockkStatic(Intent::class)
    }

    // endregion
}

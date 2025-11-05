package com.klaviyo.location

import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.FetchGeofencesResult
import com.klaviyo.analytics.networking.requests.FetchedGeofence
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONException
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

        private val stubNYC: FetchedGeofence
            get() = FetchedGeofence(
                Registry.config.apiKey,
                "NYC",
                NYC_LAT,
                NYC_LNG,
                NYC_RADIUS.toDouble()
            )

        private val stubLondon: FetchedGeofence
            get() = FetchedGeofence(
                Registry.config.apiKey,
                "LONDON",
                LONDON_LAT,
                LONDON_LNG,
                LONDON_RADIUS.toDouble()
            )

        private fun FetchedGeofence.toJson() = this.toKlaviyoGeofence().toJson().toString()

        // Helper to create JSON array of geofences
        private fun geofencesJson(vararg geofences: String) = "[${geofences.joinToString(",")}]"
    }

    private var mockApiClient: ApiClient = mockk(relaxed = true)

    private var locationManager = KlaviyoLocationManager().also {
        Registry.register<LocationManager>(it)
    }

    @Before
    override fun setup() {
        super.setup()

        Dispatchers.setMain(dispatcher)

        // Register the mocked ApiClient in the Registry
        Registry.register<ApiClient> { mockApiClient }
    }

    @After
    override fun cleanup() {
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

    // Helper method to mock successful geofence fetch
    private fun mockFetchWithResult(result: FetchGeofencesResult) {
        coEvery { mockApiClient.fetchGeofences() } returns result
        locationManager.fetchGeofences()
        dispatcher.scheduler.advanceUntilIdle()
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
    fun `offGeofencesSynced unregisters observer`() {
        var callbackInvoked = false
        val observer: GeofenceObserver = {
            callbackInvoked = true
        }

        // Register then unregister
        locationManager.onGeofenceSync(observer)
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

        locationManager.onGeofenceSync(observer1)
        locationManager.onGeofenceSync(observer2)

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

        locationManager.onGeofenceSync(observer)

        // Trigger a fetch that fails
        coEvery { mockApiClient.fetchGeofences() } returns FetchGeofencesResult.Failure
        locationManager.fetchGeofences()
        advanceUntilIdle()

        // Verify observer was NOT called
        assertEquals(false, callbackInvoked)
    }

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
        val geofences = listOf(
            stubNYC.toKlaviyoGeofence(),
            stubLondon.toKlaviyoGeofence()
        )

        // Store geofences
        locationManager.storeGeofences(geofences)

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
        locationManager.onGeofenceSync(observer)

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
}

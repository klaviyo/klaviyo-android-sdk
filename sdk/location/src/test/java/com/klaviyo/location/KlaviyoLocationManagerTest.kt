package com.klaviyo.location

import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.networking.requests.FetchGeofencesCallback
import com.klaviyo.analytics.networking.requests.FetchGeofencesResult
import com.klaviyo.analytics.networking.requests.FetchedGeofence
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
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

    private var mockApiClient: ApiClient = mockk(relaxed = true)
    private var mockApiRequest: ApiRequest = mockk(relaxed = true)
    private lateinit var locationManager: KlaviyoLocationManager

    @Before
    override fun setup() {
        super.setup()

        // Register the mocked ApiClient in the Registry
        Registry.register<ApiClient> { mockApiClient }

        // Create a real instance for testing
        locationManager = KlaviyoLocationManager()
        Registry.register<LocationManager> { locationManager }
    }

    @After
    override fun cleanup() {
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
        val callbackSlot = slot<FetchGeofencesCallback>()
        every { mockApiClient.fetchGeofences(capture(callbackSlot)) } answers {
            callbackSlot.captured(FetchGeofencesResult.Success(geofences.toList()))
            mockApiRequest
        }
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
        val callbackSlot = slot<FetchGeofencesCallback>()
        every { mockApiClient.fetchGeofences(capture(callbackSlot)) } answers {
            callbackSlot.captured(FetchGeofencesResult.Failure)
            mockApiRequest
        }

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
        val callbackSlot = slot<FetchGeofencesCallback>()

        every { mockApiClient.fetchGeofences(capture(callbackSlot)) } answers {
            // Invoke the callback with Unavailable (offline scenario)
            callbackSlot.captured(FetchGeofencesResult.Unavailable)
            mockApiRequest
        }

        // Should not crash when offline
        locationManager.fetchGeofences()

        verify { mockApiClient.fetchGeofences(any()) }
        assertEquals(true, callbackSlot.isCaptured)
    }

    @Test
    fun `fetchGeofences handles failure result without crashing`() {
        val callbackSlot = slot<FetchGeofencesCallback>()

        every { mockApiClient.fetchGeofences(capture(callbackSlot)) } answers {
            // Invoke the callback with Failure
            callbackSlot.captured(FetchGeofencesResult.Failure)
            mockApiRequest
        }

        // Should not crash on failure
        locationManager.fetchGeofences()

        verify { mockApiClient.fetchGeofences(any()) }
        assertEquals(true, callbackSlot.isCaptured)
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
}

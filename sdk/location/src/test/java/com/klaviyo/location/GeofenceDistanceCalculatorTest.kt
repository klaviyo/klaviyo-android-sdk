package com.klaviyo.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceDistanceCalculatorTest {

    // Test coordinates for Northeast US locations
    private val hartfordCT = Pair(41.7637, -72.6851)
    private val newportRI = Pair(41.4901, -71.3128)
    private val cambridgeMA = Pair(42.3736, -71.1097)
    private val bushwickNYC = Pair(40.6942, -73.9389)

    @Test
    fun `calculateDistance between Hartford and Newport is approximately correct`() {
        // Hartford, CT to Newport, RI is approximately 118 km
        val distance = GeofenceDistanceCalculator.calculateDistance(
            lat1 = hartfordCT.first,
            lon1 = hartfordCT.second,
            lat2 = newportRI.first,
            lon2 = newportRI.second
        )

        // Should be roughly 118,000 meters (within 5% tolerance)
        assertEquals(118000.0, distance, 6000.0)
    }

    @Test
    fun `calculateDistance between Cambridge and Bushwick is approximately correct`() {
        // Cambridge, MA to Bushwick, Brooklyn is approximately 300 km
        val distance = GeofenceDistanceCalculator.calculateDistance(
            lat1 = cambridgeMA.first,
            lon1 = cambridgeMA.second,
            lat2 = bushwickNYC.first,
            lon2 = bushwickNYC.second
        )

        // Should be roughly 300,500 meters (within 5% tolerance)
        assertEquals(300500.0, distance, 15000.0)
    }

    @Test
    fun `calculateDistance from a point to itself is zero`() {
        val distance = GeofenceDistanceCalculator.calculateDistance(
            lat1 = hartfordCT.first,
            lon1 = hartfordCT.second,
            lat2 = hartfordCT.first,
            lon2 = hartfordCT.second
        )

        assertEquals(0.0, distance, 0.1)
    }

    @Test
    fun `calculateDistance handles equator correctly`() {
        // Two points on the equator, 1 degree apart
        // 1 degree at equator ≈ 111 km
        val distance = GeofenceDistanceCalculator.calculateDistance(
            lat1 = 0.0,
            lon1 = 0.0,
            lat2 = 0.0,
            lon2 = 1.0
        )

        // Should be roughly 111,000 meters (within 5% tolerance)
        assertEquals(111000.0, distance, 5500.0)
    }

    @Test
    fun `calculateDistance handles antipodal points`() {
        // Points on opposite sides of earth (max distance)
        // Should be approximately half Earth's circumference ≈ 20,000 km
        val distance = GeofenceDistanceCalculator.calculateDistance(
            lat1 = 0.0,
            lon1 = 0.0,
            lat2 = 0.0,
            lon2 = 180.0
        )

        // Should be roughly 20,000,000 meters (within 5% tolerance)
        assertEquals(20000000.0, distance, 1000000.0)
    }

    @Test
    fun `filterToNearest returns empty list for empty input`() {
        val result = GeofenceDistanceCalculator.filterToNearest(
            geofences = emptyList(),
            userLatitude = hartfordCT.first,
            userLongitude = hartfordCT.second,
            limit = 20
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `filterToNearest returns all geofences when count is less than limit`() {
        val geofences = listOf(
            createGeofence("1", newportRI.first, newportRI.second),
            createGeofence("2", cambridgeMA.first, cambridgeMA.second),
            createGeofence("3", bushwickNYC.first, bushwickNYC.second)
        )

        val result = GeofenceDistanceCalculator.filterToNearest(
            geofences = geofences,
            userLatitude = hartfordCT.first,
            userLongitude = hartfordCT.second,
            limit = 20
        )

        assertEquals(3, result.size)
        assertEquals(geofences.toSet(), result.toSet())
    }

    @Test
    fun `filterToNearest returns exactly 20 geofences when more are available`() {
        // Create 25 geofences at various distances
        val geofences = (1..25).map { index ->
            createGeofence(
                id = index.toString(),
                latitude = hartfordCT.first + (index * 0.1),
                longitude = hartfordCT.second + (index * 0.1)
            )
        }

        val result = GeofenceDistanceCalculator.filterToNearest(
            geofences = geofences,
            userLatitude = hartfordCT.first,
            userLongitude = hartfordCT.second,
            limit = 20
        )

        assertEquals(20, result.size)
    }

    @Test
    fun `filterToNearest returns geofences sorted by distance`() {
        // Create geofences at various distances from Hartford
        val geofences = listOf(
            createGeofence("far", 45.0, -75.0), // Farther north
            createGeofence("medium", 42.0, -72.0), // Medium distance
            createGeofence("close", 41.5, -72.5) // Closer to Hartford
        )

        val result = GeofenceDistanceCalculator.filterToNearest(
            geofences = geofences,
            userLatitude = hartfordCT.first,
            userLongitude = hartfordCT.second,
            limit = 20
        )

        // Verify all geofences returned
        assertEquals(3, result.size)

        // Calculate distances from Hartford for each result geofence
        val distancesWithInfo = result.mapIndexed { index, geofence ->
            val distance = GeofenceDistanceCalculator.calculateDistance(
                hartfordCT.first,
                hartfordCT.second,
                geofence.latitude,
                geofence.longitude
            )
            "[$index] ${geofence.id}: ${distance / 1000}km from (${geofence.latitude}, ${geofence.longitude})"
        }

        val distances = result.map { geofence ->
            GeofenceDistanceCalculator.calculateDistance(
                hartfordCT.first,
                hartfordCT.second,
                geofence.latitude,
                geofence.longitude
            )
        }

        // Verify distances are in ascending order (closest first)
        assertTrue(
            "Distance 0 (${distances[0] / 1000}km) should be <= Distance 1 (${distances[1] / 1000}km). Order: ${distancesWithInfo.joinToString(
                ", "
            )}",
            distances[0] <= distances[1]
        )
        assertTrue("Distance 1 should be <= Distance 2", distances[1] <= distances[2])
    }

    @Test
    fun `filterToNearest filters correctly when exactly at limit`() {
        // Create exactly 20 geofences
        val geofences = (1..20).map { index ->
            createGeofence(
                id = index.toString(),
                latitude = hartfordCT.first + (index * 0.1),
                longitude = hartfordCT.second
            )
        }

        val result = GeofenceDistanceCalculator.filterToNearest(
            geofences = geofences,
            userLatitude = hartfordCT.first,
            userLongitude = hartfordCT.second,
            limit = 20
        )

        assertEquals(20, result.size)
    }

    @Test
    fun `filterToNearest respects custom limit`() {
        val geofences = (1..50).map { index ->
            createGeofence(
                id = index.toString(),
                latitude = hartfordCT.first + (index * 0.1),
                longitude = hartfordCT.second
            )
        }

        val result = GeofenceDistanceCalculator.filterToNearest(
            geofences = geofences,
            userLatitude = hartfordCT.first,
            userLongitude = hartfordCT.second,
            limit = 10
        )

        assertEquals(10, result.size)
    }

    @Test
    fun `distanceFrom extension function calculates correctly`() {
        val geofence = createGeofence("newport", newportRI.first, newportRI.second)

        val distance = geofence.distanceFrom(hartfordCT.first, hartfordCT.second)

        // Hartford to Newport is approximately 118 km
        assertEquals(118000.0, distance, 6000.0)
    }

    @Test
    fun `filterToNearest handles geofences with identical locations`() {
        // Create multiple geofences at the same location
        val geofences = listOf(
            createGeofence("1", hartfordCT.first, hartfordCT.second),
            createGeofence("2", hartfordCT.first, hartfordCT.second),
            createGeofence("3", hartfordCT.first, hartfordCT.second)
        )

        val result = GeofenceDistanceCalculator.filterToNearest(
            geofences = geofences,
            userLatitude = hartfordCT.first,
            userLongitude = hartfordCT.second,
            limit = 2
        )

        assertEquals(2, result.size)
        // All distances should be zero, so any 2 are valid
    }

    // Helper function to create test geofences
    private fun createGeofence(
        id: String,
        latitude: Double,
        longitude: Double,
        radius: Float = 100f
    ): KlaviyoGeofence {
        return KlaviyoGeofence(
            id = "TEST01:$id",
            latitude = latitude,
            longitude = longitude,
            radius = radius
        )
    }
}

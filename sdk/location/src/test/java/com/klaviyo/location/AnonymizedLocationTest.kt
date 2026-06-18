package com.klaviyo.location

import org.junit.Assert.assertEquals
import org.junit.Test

class AnonymizedLocationTest {

    @Test
    fun `fromCoordinates rounds Hartford CT to nearest 0_145 degrees`() {
        // Hartford, CT: 41.7637° N, 72.6851° W
        // 41.7637 / 0.145 = 288.01, rounds to 288, 288 * 0.145 = 41.76
        // -72.6851 / 0.145 = -501.28, rounds to -501, -501 * 0.145 = -72.645
        val anonymized = AnonymizedLocation.fromCoordinates(
            latitude = 41.7637,
            longitude = -72.6851
        )

        assertEquals(41.76, anonymized.latitude, 0.01)
        assertEquals(-72.645, anonymized.longitude, 0.01)
    }

    @Test
    fun `fromCoordinates rounds Newport RI to nearest 0_145 degrees`() {
        // Newport, RI: 41.4901° N, 71.3128° W
        // 41.4901 / 0.145 = 286.14, rounds to 286, 286 * 0.145 = 41.47
        // -71.3128 / 0.145 = -491.81, rounds to -492, -492 * 0.145 = -71.34
        val anonymized = AnonymizedLocation.fromCoordinates(
            latitude = 41.4901,
            longitude = -71.3128
        )

        assertEquals(41.47, anonymized.latitude, 0.01)
        assertEquals(-71.34, anonymized.longitude, 0.01)
    }

    @Test
    fun `fromCoordinates rounds Cambridge MA to nearest 0_145 degrees`() {
        // Cambridge, MA: 42.3736° N, 71.1097° W
        // 42.3736 / 0.145 = 292.23, rounds to 292, 292 * 0.145 = 42.34
        // -71.1097 / 0.145 = -490.41, rounds to -490, -490 * 0.145 = -71.05
        val anonymized = AnonymizedLocation.fromCoordinates(
            latitude = 42.3736,
            longitude = -71.1097
        )

        assertEquals(42.34, anonymized.latitude, 0.01)
        assertEquals(-71.05, anonymized.longitude, 0.01)
    }

    @Test
    fun `fromCoordinates rounds Bushwick NYC to nearest 0_145 degrees`() {
        // Bushwick, Brooklyn, NYC: 40.6942° N, 73.9389° W
        // 40.6942 / 0.145 = 280.64, rounds to 281, 281 * 0.145 = 40.745
        // -73.9389 / 0.145 = -509.92, rounds to -510, -510 * 0.145 = -73.95
        val anonymized = AnonymizedLocation.fromCoordinates(
            latitude = 40.6942,
            longitude = -73.9389
        )

        assertEquals(40.745, anonymized.latitude, 0.01)
        assertEquals(-73.95, anonymized.longitude, 0.01)
    }

    @Test
    fun `rounds to nearest increment correctly`() {
        // Test a coordinate that should round down
        val roundedDown = AnonymizedLocation.fromCoordinates(
            latitude = 41.80, // 41.80 / 0.145 = 288.28, rounds to 288, 288 * 0.145 = 41.76
            longitude = -72.60 // -72.60 / 0.145 = -500.69, rounds to -501, -501 * 0.145 = -72.645
        )

        assertEquals(41.76, roundedDown.latitude, 0.01)
        assertEquals(-72.645, roundedDown.longitude, 0.01)

        // Test a coordinate that should round up
        val roundedUp = AnonymizedLocation.fromCoordinates(
            latitude = 42.00, // 42.00 / 0.145 = 289.66, rounds to 290, 290 * 0.145 = 42.05
            longitude = -71.00 // -71.00 / 0.145 = -489.66, rounds to -490, -490 * 0.145 = -71.05
        )

        assertEquals(42.05, roundedUp.latitude, 0.01)
        assertEquals(-71.05, roundedUp.longitude, 0.01)
    }

    @Test
    fun `handles exact multiples of increment`() {
        // Test coordinates that are exact multiples of 0.145
        val exactMultiple = AnonymizedLocation.fromCoordinates(
            latitude = 42.05, // 42.05 / 0.145 = 290.0, stays as 290, 290 * 0.145 = 42.05
            longitude = -71.05 // -71.05 / 0.145 = -490.0, stays as -490, -490 * 0.145 = -71.05
        )

        assertEquals(42.05, exactMultiple.latitude, 0.01)
        assertEquals(-71.05, exactMultiple.longitude, 0.01)
    }

    @Test
    fun `handles coordinates at equator`() {
        // 0.0 / 0.145 = 0, 0 * 0.145 = 0.0
        val anonymized = AnonymizedLocation.fromCoordinates(
            latitude = 0.0,
            longitude = 0.0
        )

        assertEquals(0.0, anonymized.latitude, 0.01)
        assertEquals(0.0, anonymized.longitude, 0.01)
    }

    @Test
    fun `handles coordinates at poles with clamping`() {
        // 90.0 / 0.145 = 620.69, rounds to 621, 621 * 0.145 = 90.045
        // BUT 90.045 exceeds valid latitude range, so it's clamped to 90.0
        // Same for south pole: -90.045 clamped to -90.0
        val northPole = AnonymizedLocation.fromCoordinates(90.0, 0.0)
        val southPole = AnonymizedLocation.fromCoordinates(-90.0, 0.0)

        assertEquals(90.0, northPole.latitude, 0.01)
        assertEquals(-90.0, southPole.latitude, 0.01)
    }

    @Test
    fun `handles coordinates at international date line with clamping`() {
        // 180.0 / 0.145 = 1241.38, rounds to 1241, 1241 * 0.145 = 179.945
        // -180.0 / 0.145 = -1241.38, rounds to -1241, -1241 * 0.145 = -179.945
        // Both remain within valid longitude range [-180, 180] so no clamping needed
        val east = AnonymizedLocation.fromCoordinates(0.0, 180.0)
        val west = AnonymizedLocation.fromCoordinates(0.0, -180.0)

        assertEquals(179.945, east.longitude, 0.01)
        assertEquals(-179.945, west.longitude, 0.01)
    }

    @Test
    fun `data class equality works correctly`() {
        val location1 = AnonymizedLocation(37.77, -122.42)
        val location2 = AnonymizedLocation(37.77, -122.42)
        val location3 = AnonymizedLocation(37.78, -122.42)

        assertEquals(location1, location2)
        assert(location1 != location3)
    }

    @Test
    fun `provides approximately 10 mile precision`() {
        // Verify that 0.145 degrees is approximately 10 miles at mid-latitudes
        // At 40°N latitude:
        // 1 degree latitude ≈ 69 miles
        // 0.145 degrees latitude ≈ 10 miles
        val lat1 = 40.0
        val lat2 = 40.145

        val anonymized1 = AnonymizedLocation.fromCoordinates(lat1, -74.0)
        val anonymized2 = AnonymizedLocation.fromCoordinates(lat2, -74.0)

        // The difference should be exactly 0.145 degrees
        val latDiff = anonymized2.latitude - anonymized1.latitude
        assertEquals(0.145, latDiff, 0.01)
    }
}

package com.klaviyo.location

import org.junit.Assert.assertEquals
import org.junit.Test

class AnonymizedLocationTest {

    @Test
    fun `fromCoordinates rounds Hartford CT to 2 decimal places`() {
        // Hartford, CT: 41.7637° N, 72.6851° W
        val anonymized = AnonymizedLocation.fromCoordinates(
            latitude = 41.7637,
            longitude = -72.6851
        )

        assertEquals(41.76, anonymized.latitude, 0.001)
        assertEquals(-72.69, anonymized.longitude, 0.001)
    }

    @Test
    fun `fromCoordinates rounds Newport RI to 2 decimal places`() {
        // Newport, RI: 41.4901° N, 71.3128° W
        val anonymized = AnonymizedLocation.fromCoordinates(
            latitude = 41.4901,
            longitude = -71.3128
        )

        assertEquals(41.49, anonymized.latitude, 0.001)
        assertEquals(-71.31, anonymized.longitude, 0.001)
    }

    @Test
    fun `fromCoordinates rounds Cambridge MA to 2 decimal places`() {
        // Cambridge, MA: 42.3736° N, 71.1097° W
        val anonymized = AnonymizedLocation.fromCoordinates(
            latitude = 42.3736,
            longitude = -71.1097
        )

        assertEquals(42.37, anonymized.latitude, 0.001)
        assertEquals(-71.11, anonymized.longitude, 0.001)
    }

    @Test
    fun `fromCoordinates rounds Bushwick NYC to 2 decimal places`() {
        // Bushwick, Brooklyn, NYC: 40.6942° N, 73.9389° W
        val anonymized = AnonymizedLocation.fromCoordinates(
            latitude = 40.6942,
            longitude = -73.9389
        )

        assertEquals(40.69, anonymized.latitude, 0.001)
        assertEquals(-73.94, anonymized.longitude, 0.001)
    }

    @Test
    fun `rounds down when third decimal is less than 5`() {
        val anonymized = AnonymizedLocation.fromCoordinates(
            latitude = 41.764, // Should round to 41.76
            longitude = -72.684 // Should round to -72.68
        )

        assertEquals(41.76, anonymized.latitude, 0.001)
        assertEquals(-72.68, anonymized.longitude, 0.001)
    }

    @Test
    fun `rounds up when third decimal is 5 or greater`() {
        val anonymized = AnonymizedLocation.fromCoordinates(
            latitude = 41.765, // Should round to 41.77
            longitude = -72.686 // Should round to -72.69 (need >5 for negative numbers)
        )

        assertEquals(41.77, anonymized.latitude, 0.001)
        assertEquals(-72.69, anonymized.longitude, 0.001)
    }

    @Test
    fun `handles coordinates at equator`() {
        val anonymized = AnonymizedLocation.fromCoordinates(
            latitude = 0.0,
            longitude = 0.0
        )

        assertEquals(0.0, anonymized.latitude, 0.001)
        assertEquals(0.0, anonymized.longitude, 0.001)
    }

    @Test
    fun `handles coordinates at poles`() {
        val northPole = AnonymizedLocation.fromCoordinates(90.0, 0.0)
        val southPole = AnonymizedLocation.fromCoordinates(-90.0, 0.0)

        assertEquals(90.0, northPole.latitude, 0.001)
        assertEquals(-90.0, southPole.latitude, 0.001)
    }

    @Test
    fun `handles coordinates at international date line`() {
        val east = AnonymizedLocation.fromCoordinates(0.0, 180.0)
        val west = AnonymizedLocation.fromCoordinates(0.0, -180.0)

        assertEquals(180.0, east.longitude, 0.001)
        assertEquals(-180.0, west.longitude, 0.001)
    }

    @Test
    fun `data class equality works correctly`() {
        val location1 = AnonymizedLocation(37.77, -122.42)
        val location2 = AnonymizedLocation(37.77, -122.42)
        val location3 = AnonymizedLocation(37.78, -122.42)

        assertEquals(location1, location2)
        assert(location1 != location3)
    }
}

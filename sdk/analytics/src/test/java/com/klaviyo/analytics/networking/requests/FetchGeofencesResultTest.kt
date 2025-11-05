package com.klaviyo.analytics.networking.requests

import com.klaviyo.fixtures.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

internal class FetchGeofencesResultTest : BaseTest() {

    @Test
    fun `Success result contains geofence data`() {
        val geofences = listOf(
            FetchedGeofence(API_KEY, "id1", 40.7, -74.0, 100.0),
            FetchedGeofence(API_KEY, "id2", 50.1, -120.2, 200.0)
        )
        val result = FetchGeofencesResult.Success(geofences)

        assertEquals(2, result.data.size)
        assertEquals("id1", result.data[0].id)
        assertEquals("id2", result.data[1].id)
    }

    @Test
    fun `Success result with empty list`() {
        val result = FetchGeofencesResult.Success(emptyList())

        assertEquals(0, result.data.size)
    }

    @Test
    fun `Success result data is immutable list`() {
        val geofences = listOf(FetchedGeofence(API_KEY, "id1", 40.7, -74.0, 100.0))
        val result = FetchGeofencesResult.Success(geofences)

        // List should be the same reference
        assertEquals(geofences, result.data)
    }

    @Test
    fun `Success result data access`() {
        val geofence1 = FetchedGeofence(API_KEY, "id1", 40.7, -74.0, 100.0)
        val geofence2 = FetchedGeofence(API_KEY, "id2", 50.1, -120.2, 200.0)
        val result = FetchGeofencesResult.Success(listOf(geofence1, geofence2))

        val data = result.data
        assertEquals(geofence1, data[0])
        assertEquals(geofence2, data[1])
    }

    @Test
    fun `Success result maintains data ordering`() {
        val geofences = (1..10).map { i ->
            FetchedGeofence(API_KEY, "id$i", (40.0 + i), (-74.0 + i), (100.0 * i))
        }
        val result = FetchGeofencesResult.Success(geofences)

        assertEquals(10, result.data.size)
        for (i in 0 until 10) {
            assertEquals("id${i + 1}", result.data[i].id)
        }
    }

    @Test
    fun `Success equality based on data`() {
        val geofences1 = listOf(FetchedGeofence(API_KEY, "id1", 40.7, -74.0, 100.0))
        val geofences2 = listOf(FetchedGeofence(API_KEY, "id1", 40.7, -74.0, 100.0))
        val geofences3 = listOf(FetchedGeofence(API_KEY, "id2", 40.7, -74.0, 100.0))

        val success1 = FetchGeofencesResult.Success(geofences1)
        val success2 = FetchGeofencesResult.Success(geofences2)
        val success3 = FetchGeofencesResult.Success(geofences3)

        assertEquals(success1, success2)
        assertNotEquals(success1, success3)
    }
}

package com.klaviyo.analytics.networking.requests

import com.klaviyo.fixtures.BaseTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class FetchedGeofenceTest : BaseTest() {

    @Test
    fun `creates FetchedGeofence with correct values`() {
        val geofence = FetchedGeofence(
            companyId = API_KEY,
            id = "test-id",
            latitude = 40.7128,
            longitude = -74.006,
            radius = 100.0
        )

        assertEquals(API_KEY, geofence.companyId)
        assertEquals("test-id", geofence.id)
        assertEquals(40.7128, geofence.latitude, 0.0001)
        assertEquals(-74.006, geofence.longitude, 0.0001)
        assertEquals(100.0, geofence.radius, 0.0001)
    }

    @Test
    fun `toFetchedGeofence extension parses valid JSON-API structure`() {
        val json = JSONObject(
            """
            {
                "type": "geofence",
                "id": "8db4effa-44f1-45e6-a88d-8e7d50516a0f",
                "attributes": {
                    "latitude": 40.7128,
                    "longitude": -74.006,
                    "radius": 100.5
                }
            }
        """
        )

        val geofence = json.toFetchedGeofence(API_KEY)

        assertNotNull(geofence)
        assertEquals(API_KEY, geofence!!.companyId)
        assertEquals("8db4effa-44f1-45e6-a88d-8e7d50516a0f", geofence.id)
        assertEquals(40.7128, geofence.latitude, 0.0001)
        assertEquals(-74.006, geofence.longitude, 0.0001)
        assertEquals(100.5, geofence.radius, 0.0001)
    }

    @Test
    fun `toFetchedGeofence handles negative coordinates`() {
        val json = JSONObject(
            """
            {
                "type": "geofence",
                "id": "test-id",
                "attributes": {
                    "latitude": -33.8688,
                    "longitude": 151.2093,
                    "radius": 500.0
                }
            }
        """
        )

        val geofence = json.toFetchedGeofence(API_KEY)

        assertNotNull(geofence)
        assertEquals(API_KEY, geofence!!.companyId)
        assertEquals("test-id", geofence.id)
        assertEquals(-33.8688, geofence.latitude, 0.0001)
        assertEquals(151.2093, geofence.longitude, 0.0001)
        assertEquals(500.0, geofence.radius, 0.0001)
    }

    @Test
    fun `toFetchedGeofence handles zero coordinates`() {
        val json = JSONObject(
            """
            {
                "type": "geofence",
                "id": "equator-id",
                "attributes": {
                    "latitude": 0.0,
                    "longitude": 0.0,
                    "radius": 1.0
                }
            }
        """
        )

        val geofence = json.toFetchedGeofence(API_KEY)

        assertNotNull(geofence)
        assertEquals(API_KEY, geofence!!.companyId)
        assertEquals(0.0, geofence.latitude, 0.0001)
        assertEquals(0.0, geofence.longitude, 0.0001)
    }

    @Test
    fun `toFetchedGeofences parses array of geofences`() {
        val jsonArray = JSONArray(
            """
            [
                {
                    "type": "geofence",
                    "id": "id1",
                    "attributes": {
                        "latitude": 40.7,
                        "longitude": -74.0,
                        "radius": 100.0
                    }
                },
                {
                    "type": "geofence",
                    "id": "id2",
                    "attributes": {
                        "latitude": 50.1,
                        "longitude": -120.2,
                        "radius": 200.0
                    }
                }
            ]
        """
        )

        val geofences = jsonArray.toFetchedGeofences(API_KEY)

        assertEquals(2, geofences.size)
        assertEquals(API_KEY, geofences[0].companyId)
        assertEquals("id1", geofences[0].id)
        assertEquals(API_KEY, geofences[1].companyId)
        assertEquals("id2", geofences[1].id)
    }

    @Test
    fun `toFetchedGeofences returns empty list for empty array`() {
        val jsonArray = JSONArray("[]")

        val geofences = jsonArray.toFetchedGeofences(API_KEY)

        assertEquals(0, geofences.size)
    }

    @Test
    fun `toFetchedGeofences filters out invalid entries`() {
        val jsonArray = JSONArray(
            """
            [
                {
                    "type": "geofence",
                    "id": "id1",
                    "attributes": {
                        "latitude": 40.7,
                        "longitude": -74.0,
                        "radius": 100.0
                    }
                },
                {
                    "type": "geofence",
                    "id": "id2"
                },
                {
                    "type": "geofence",
                    "id": "id3",
                    "attributes": {
                        "latitude": 50.1,
                        "longitude": -120.2,
                        "radius": 200.0
                    }
                }
            ]
        """
        )

        val geofences = jsonArray.toFetchedGeofences(API_KEY)

        // Should only include valid entries
        assertEquals(2, geofences.size)
        assertEquals(API_KEY, geofences[0].companyId)
        assertEquals("id1", geofences[0].id)
        assertEquals(API_KEY, geofences[1].companyId)
        assertEquals("id3", geofences[1].id)
    }

    @Test
    fun `data class equality works correctly`() {
        val geofence1 = FetchedGeofence(API_KEY, "id", 40.7, -74.0, 100.0)
        val geofence2 = FetchedGeofence(API_KEY, "id", 40.7, -74.0, 100.0)
        val geofence3 = FetchedGeofence(API_KEY, "other-id", 40.7, -74.0, 100.0)

        assertEquals(geofence1, geofence2)
        assert(geofence1 != geofence3)
    }

    @Test
    fun `data class toString works correctly`() {
        val geofence = FetchedGeofence(API_KEY, "test-id", 40.7128, -74.006, 100.5)
        val stringRep = geofence.toString()

        assert(stringRep.contains(API_KEY))
        assert(stringRep.contains("test-id"))
        assert(stringRep.contains("40.7128"))
        assert(stringRep.contains("-74.006"))
        assert(stringRep.contains("100.5"))
    }
}

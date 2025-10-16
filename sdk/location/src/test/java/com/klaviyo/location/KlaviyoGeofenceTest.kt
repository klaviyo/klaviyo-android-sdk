package com.klaviyo.location

import com.google.android.gms.location.Geofence
import com.klaviyo.analytics.networking.requests.FetchedGeofence
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class KlaviyoGeofenceTest : BaseTest() {

    companion object {
        private const val TEST_ID = "company123-location456"
        private const val TEST_LATITUDE = 40.7128
        private const val TEST_LONGITUDE = -74.006
        private const val TEST_RADIUS = 100.0f
    }

    private fun createTestGeofence(
        id: String = TEST_ID,
        latitude: Double = TEST_LATITUDE,
        longitude: Double = TEST_LONGITUDE,
        radius: Float = TEST_RADIUS
    ) = KlaviyoGeofence(id = id, latitude = latitude, longitude = longitude, radius = radius)

    @Test
    fun `parses company ID from composite ID`() {
        val geofence = createTestGeofence()

        assertEquals("company123", geofence.companyId)
    }

    @Test
    fun `parses location ID from composite ID`() {
        val geofence = createTestGeofence()

        assertEquals("location456", geofence.locationId)
    }

    @Test
    fun `toJson serializes geofence data`() {
        val geofence = createTestGeofence()
        val json = geofence.toJson()

        assertEquals(TEST_ID, json.getString("id"))
        assertEquals(TEST_LATITUDE, json.getDouble("latitude"), 0.0001)
        assertEquals(TEST_LONGITUDE, json.getDouble("longitude"), 0.0001)
        assertEquals(TEST_RADIUS.toDouble(), json.getDouble("radius"), 0.0001)
    }

    @Test
    fun `converts FetchedGeofence to KlaviyoGeofence`() {
        val klaviyoGeofence = FetchedGeofence(
            id = "location123",
            latitude = TEST_LATITUDE,
            longitude = TEST_LONGITUDE,
            radius = TEST_RADIUS.toDouble()
        ).toKlaviyoGeofence()

        assertEquals(mockConfig.apiKey + "-location123", klaviyoGeofence.id)
        assertEquals(TEST_LATITUDE, klaviyoGeofence.latitude, 0.0001)
        assertEquals(TEST_LONGITUDE, klaviyoGeofence.longitude, 0.0001)
        assertEquals(TEST_RADIUS, klaviyoGeofence.radius)
    }

    @Test
    fun `converts Geofence to KlaviyoGeofence`() {
        val klaviyoGeofence = mockk<Geofence>().apply {
            every { requestId } returns "company-location123"
            every { latitude } returns TEST_LATITUDE
            every { longitude } returns TEST_LONGITUDE
            every { radius } returns TEST_RADIUS
        }.toKlaviyoGeofence()

        assertEquals("company-location123", klaviyoGeofence.id)
        assertEquals(TEST_LATITUDE, klaviyoGeofence.latitude, 0.0001)
        assertEquals(TEST_LONGITUDE, klaviyoGeofence.longitude, 0.0001)
        assertEquals(TEST_RADIUS, klaviyoGeofence.radius)
    }

    @Test
    fun `data class equality works correctly`() {
        val geofence1 = createTestGeofence()
        val geofence2 = createTestGeofence()
        val geofence3 = createTestGeofence(id = "other-id")

        assertEquals(geofence1, geofence2)
        assertNotEquals(geofence1, geofence3)
    }

    @Test
    fun `JSONObject toKlaviyoGeofence deserializes valid JSON`() {
        val json = JSONObject().apply {
            put(KlaviyoGeofence.KEY_ID, TEST_ID)
            put(KlaviyoGeofence.KEY_LATITUDE, TEST_LATITUDE)
            put(KlaviyoGeofence.KEY_LONGITUDE, TEST_LONGITUDE)
            put(KlaviyoGeofence.KEY_RADIUS, TEST_RADIUS.toDouble())
        }

        val geofence = json.toKlaviyoGeofence()

        assertEquals(TEST_ID, geofence!!.id)
        assertEquals(TEST_LATITUDE, geofence.latitude, 0.0001)
        assertEquals(TEST_LONGITUDE, geofence.longitude, 0.0001)
        assertEquals(TEST_RADIUS, geofence.radius)
    }

    @Test
    fun `round-trip serialization and deserialization preserves data`() {
        val originalGeofence = createTestGeofence()
        val json = originalGeofence.toJson()

        val deserializedGeofence = json.toKlaviyoGeofence()

        assertEquals(originalGeofence.id, deserializedGeofence!!.id)
        assertEquals(originalGeofence.latitude, deserializedGeofence.latitude, 0.0001)
        assertEquals(originalGeofence.longitude, deserializedGeofence.longitude, 0.0001)
        assertEquals(originalGeofence.radius, deserializedGeofence.radius)
    }

    @Test
    fun `toKlaviyoGeofence returns null when ID field is missing`() {
        val json = JSONObject().apply {
            put(KlaviyoGeofence.KEY_LATITUDE, TEST_LATITUDE)
            put(KlaviyoGeofence.KEY_LONGITUDE, TEST_LONGITUDE)
            put(KlaviyoGeofence.KEY_RADIUS, TEST_RADIUS.toDouble())
        }

        val geofence = json.toKlaviyoGeofence()

        assertNull(geofence)
    }

    @Test
    fun `toKlaviyoGeofence returns null when latitude field is missing`() {
        val json = JSONObject().apply {
            put(KlaviyoGeofence.KEY_ID, TEST_ID)
            put(KlaviyoGeofence.KEY_LONGITUDE, TEST_LONGITUDE)
            put(KlaviyoGeofence.KEY_RADIUS, TEST_RADIUS.toDouble())
        }

        val geofence = json.toKlaviyoGeofence()

        assertNull(geofence)
    }

    @Test
    fun `toKlaviyoGeofence returns null when radius field has invalid type`() {
        val json = JSONObject().apply {
            put(KlaviyoGeofence.KEY_ID, TEST_ID)
            put(KlaviyoGeofence.KEY_LATITUDE, TEST_LATITUDE)
            put(KlaviyoGeofence.KEY_LONGITUDE, TEST_LONGITUDE)
            put(KlaviyoGeofence.KEY_RADIUS, "not-a-number")
        }

        val geofence = json.toKlaviyoGeofence()

        assertNull(geofence)
    }

    @Test
    fun `toKlaviyoGeofence handles negative coordinates`() {
        val json = JSONObject().apply {
            put(KlaviyoGeofence.KEY_ID, "test-id")
            put(KlaviyoGeofence.KEY_LATITUDE, -40.7128)
            put(KlaviyoGeofence.KEY_LONGITUDE, 74.006)
            put(KlaviyoGeofence.KEY_RADIUS, 50.0)
        }

        val geofence = json.toKlaviyoGeofence()

        assertEquals("test-id", geofence!!.id)
        assertEquals(-40.7128, geofence.latitude, 0.0001)
        assertEquals(74.006, geofence.longitude, 0.0001)
        assertEquals(50.0f, geofence.radius)
    }
}

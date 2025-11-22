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
        private const val TEST_ID = "$API_KEY:location456"
        private const val TEST_LATITUDE = 40.7128
        private const val TEST_LONGITUDE = -74.006
        private const val TEST_RADIUS = 100.0f
    }

    private fun createTestGeofence(
        id: String = TEST_ID,
        latitude: Double = TEST_LATITUDE,
        longitude: Double = TEST_LONGITUDE,
        radius: Float = TEST_RADIUS,
        duration: Int? = null
    ) = KlaviyoGeofence(
        id = id,
        latitude = latitude,
        longitude = longitude,
        radius = radius,
        duration = duration
    )

    @Test
    fun `parses company ID from composite ID`() {
        val geofence = createTestGeofence()

        assertEquals(API_KEY, geofence.companyId)
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
            companyId = mockConfig.apiKey,
            id = "location123",
            latitude = TEST_LATITUDE,
            longitude = TEST_LONGITUDE,
            radius = TEST_RADIUS.toDouble()
        ).toKlaviyoGeofence()

        assertEquals(mockConfig.apiKey + ":location123", klaviyoGeofence.id)
        assertEquals(TEST_LATITUDE, klaviyoGeofence.latitude, 0.0001)
        assertEquals(TEST_LONGITUDE, klaviyoGeofence.longitude, 0.0001)
        assertEquals(TEST_RADIUS, klaviyoGeofence.radius)
    }

    @Test
    fun `converts Geofence to KlaviyoGeofence`() {
        val klaviyoGeofence = mockk<Geofence>().apply {
            every { requestId } returns "$API_KEY:location123"
            every { latitude } returns TEST_LATITUDE
            every { longitude } returns TEST_LONGITUDE
            every { radius } returns TEST_RADIUS
        }.toKlaviyoGeofence()

        assertEquals("$API_KEY:location123", klaviyoGeofence.id)
        assertEquals(TEST_LATITUDE, klaviyoGeofence.latitude, 0.0001)
        assertEquals(TEST_LONGITUDE, klaviyoGeofence.longitude, 0.0001)
        assertEquals(TEST_RADIUS, klaviyoGeofence.radius)
    }

    @Test
    fun `data class equality works correctly`() {
        val geofence1 = createTestGeofence()
        val geofence2 = createTestGeofence()
        val geofence3 = createTestGeofence(id = "$API_KEY:other-id")

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
            put(KlaviyoGeofence.KEY_ID, "$API_KEY:test-id")
            put(KlaviyoGeofence.KEY_LATITUDE, -40.7128)
            put(KlaviyoGeofence.KEY_LONGITUDE, 74.006)
            put(KlaviyoGeofence.KEY_RADIUS, 50.0)
        }

        val geofence = json.toKlaviyoGeofence()

        assertEquals("$API_KEY:test-id", geofence!!.id)
        assertEquals(-40.7128, geofence.latitude, 0.0001)
        assertEquals(74.006, geofence.longitude, 0.0001)
        assertEquals(50.0f, geofence.radius)
    }

    @Test
    fun `handles locationId containing colons`() {
        val geofence = createTestGeofence(id = "$API_KEY:loc:123:456")

        assertEquals(API_KEY, geofence.companyId)
        assertEquals("loc:123:456", geofence.locationId)
    }

    @Test
    fun `logs warning when ID has no colon separator`() {
        // This should still create the geofence but log a warning
        val geofence = createTestGeofence(id = "invalidformat")

        assertEquals("invalidformat", geofence.companyId)
        assertEquals("", geofence.locationId)
    }

    @Test
    fun `logs warning when companyId is not 6 characters`() {
        // This should still create the geofence but log a warning
        val geofence = createTestGeofence(id = "short:location123")

        assertEquals("short", geofence.companyId)
        assertEquals("location123", geofence.locationId)
    }

    @Test
    fun `logs warning when locationId is empty`() {
        // This should still create the geofence but log a warning
        val geofence = createTestGeofence(id = "$API_KEY:")

        assertEquals(API_KEY, geofence.companyId)
        assertEquals("", geofence.locationId)
    }

    @Test
    fun `valid format with 6-char companyId and non-empty locationId`() {
        val geofence = createTestGeofence(id = "aBcDeF:loc123")

        assertEquals("aBcDeF", geofence.companyId)
        assertEquals("loc123", geofence.locationId)
    }

    @Test
    fun `creates KlaviyoGeofence with duration`() {
        val geofence = createTestGeofence(duration = 60)

        assertEquals(TEST_ID, geofence.id)
        assertEquals(TEST_LATITUDE, geofence.latitude, 0.0001)
        assertEquals(TEST_LONGITUDE, geofence.longitude, 0.0001)
        assertEquals(TEST_RADIUS, geofence.radius)
        assertEquals(60, geofence.duration)
    }

    @Test
    fun `creates KlaviyoGeofence without duration defaults to null`() {
        val geofence = createTestGeofence()

        assertNull(geofence.duration)
    }

    @Test
    fun `toJson serializes duration when present`() {
        val geofence = createTestGeofence(duration = 30)
        val json = geofence.toJson()

        assertEquals(TEST_ID, json.getString("id"))
        assertEquals(TEST_LATITUDE, json.getDouble("latitude"), 0.0001)
        assertEquals(TEST_LONGITUDE, json.getDouble("longitude"), 0.0001)
        assertEquals(TEST_RADIUS.toDouble(), json.getDouble("radius"), 0.0001)
        assertEquals(30, json.getInt("duration"))
    }

    @Test
    fun `toJson omits duration when null`() {
        val geofence = createTestGeofence(duration = null)
        val json = geofence.toJson()

        assertEquals(TEST_ID, json.getString("id"))
        assertEquals(TEST_LATITUDE, json.getDouble("latitude"), 0.0001)
        assertEquals(TEST_LONGITUDE, json.getDouble("longitude"), 0.0001)
        assertEquals(TEST_RADIUS.toDouble(), json.getDouble("radius"), 0.0001)
        assert(!json.has("duration"))
    }

    @Test
    fun `converts FetchedGeofence with duration to KlaviyoGeofence`() {
        val klaviyoGeofence = FetchedGeofence(
            companyId = mockConfig.apiKey,
            id = "location123",
            latitude = TEST_LATITUDE,
            longitude = TEST_LONGITUDE,
            radius = TEST_RADIUS.toDouble(),
            duration = 90
        ).toKlaviyoGeofence()

        assertEquals(mockConfig.apiKey + ":location123", klaviyoGeofence.id)
        assertEquals(TEST_LATITUDE, klaviyoGeofence.latitude, 0.0001)
        assertEquals(TEST_LONGITUDE, klaviyoGeofence.longitude, 0.0001)
        assertEquals(TEST_RADIUS, klaviyoGeofence.radius)
        assertEquals(90, klaviyoGeofence.duration)
    }

    @Test
    fun `converts Geofence to KlaviyoGeofence has null duration`() {
        val klaviyoGeofence = mockk<Geofence>().apply {
            every { requestId } returns "$API_KEY:location123"
            every { latitude } returns TEST_LATITUDE
            every { longitude } returns TEST_LONGITUDE
            every { radius } returns TEST_RADIUS
        }.toKlaviyoGeofence()

        assertEquals("$API_KEY:location123", klaviyoGeofence.id)
        assertEquals(TEST_LATITUDE, klaviyoGeofence.latitude, 0.0001)
        assertEquals(TEST_LONGITUDE, klaviyoGeofence.longitude, 0.0001)
        assertEquals(TEST_RADIUS, klaviyoGeofence.radius)
        assertNull(klaviyoGeofence.duration)
    }

    @Test
    fun `JSONObject toKlaviyoGeofence deserializes duration when present`() {
        val json = JSONObject().apply {
            put(KlaviyoGeofence.KEY_ID, TEST_ID)
            put(KlaviyoGeofence.KEY_LATITUDE, TEST_LATITUDE)
            put(KlaviyoGeofence.KEY_LONGITUDE, TEST_LONGITUDE)
            put(KlaviyoGeofence.KEY_RADIUS, TEST_RADIUS.toDouble())
            put(KlaviyoGeofence.KEY_DURATION, 45)
        }

        val geofence = json.toKlaviyoGeofence()

        assertEquals(TEST_ID, geofence!!.id)
        assertEquals(TEST_LATITUDE, geofence.latitude, 0.0001)
        assertEquals(TEST_LONGITUDE, geofence.longitude, 0.0001)
        assertEquals(TEST_RADIUS, geofence.radius)
        assertEquals(45, geofence.duration)
    }

    @Test
    fun `JSONObject toKlaviyoGeofence handles missing duration as null`() {
        val json = JSONObject().apply {
            put(KlaviyoGeofence.KEY_ID, TEST_ID)
            put(KlaviyoGeofence.KEY_LATITUDE, TEST_LATITUDE)
            put(KlaviyoGeofence.KEY_LONGITUDE, TEST_LONGITUDE)
            put(KlaviyoGeofence.KEY_RADIUS, TEST_RADIUS.toDouble())
        }

        val geofence = json.toKlaviyoGeofence()

        assertEquals(TEST_ID, geofence!!.id)
        assertNull(geofence.duration)
    }

    @Test
    fun `JSONObject toKlaviyoGeofence handles explicit null duration`() {
        val json = JSONObject().apply {
            put(KlaviyoGeofence.KEY_ID, TEST_ID)
            put(KlaviyoGeofence.KEY_LATITUDE, TEST_LATITUDE)
            put(KlaviyoGeofence.KEY_LONGITUDE, TEST_LONGITUDE)
            put(KlaviyoGeofence.KEY_RADIUS, TEST_RADIUS.toDouble())
            put(KlaviyoGeofence.KEY_DURATION, JSONObject.NULL)
        }

        val geofence = json.toKlaviyoGeofence()

        assertEquals(TEST_ID, geofence!!.id)
        assertNull(geofence.duration)
    }

    @Test
    fun `round-trip serialization preserves duration`() {
        val originalGeofence = createTestGeofence(duration = 120)
        val json = originalGeofence.toJson()
        val deserializedGeofence = json.toKlaviyoGeofence()

        assertEquals(originalGeofence.id, deserializedGeofence!!.id)
        assertEquals(originalGeofence.latitude, deserializedGeofence.latitude, 0.0001)
        assertEquals(originalGeofence.longitude, deserializedGeofence.longitude, 0.0001)
        assertEquals(originalGeofence.radius, deserializedGeofence.radius)
        assertEquals(originalGeofence.duration, deserializedGeofence.duration)
    }

    @Test
    fun `round-trip serialization preserves null duration`() {
        val originalGeofence = createTestGeofence(duration = null)
        val json = originalGeofence.toJson()
        val deserializedGeofence = json.toKlaviyoGeofence()

        assertEquals(originalGeofence.id, deserializedGeofence!!.id)
        assertNull(deserializedGeofence.duration)
    }

    @Test
    fun `data class equality considers duration`() {
        val geofence1 = createTestGeofence(duration = 60)
        val geofence2 = createTestGeofence(duration = 60)
        val geofence3 = createTestGeofence(duration = null)
        val geofence4 = createTestGeofence(duration = 120)

        assertEquals(geofence1, geofence2)
        assertNotEquals(geofence1, geofence3)
        assertNotEquals(geofence1, geofence4)
    }
}

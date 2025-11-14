package com.klaviyo.location

import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceCooldownTrackerTest : BaseTest() {
    private val tracker = GeofenceCooldownTracker()
    private val geofenceId = "test_geofence_id"

    @Test
    fun `isAllowed returns true on first transition with no previous entry`() {
        // No previous cooldown entry exists
        Registry.dataStore.clear("geofence_cooldowns")

        // Should allow first transition
        assertTrue(tracker.isAllowed(geofenceId, KlaviyoGeofenceTransition.Entered))
    }

    @Test
    fun `isAllowed returns false within cooldown period`() {
        // Store a recent transition (30 seconds ago)
        val cooldownMap = JSONObject().apply {
            put("$geofenceId:Entered", TIME - 30_000)
        }
        Registry.dataStore.store("geofence_cooldowns", cooldownMap.toString())

        // Should block transition within cooldown
        assertFalse(tracker.isAllowed(geofenceId, KlaviyoGeofenceTransition.Entered))
    }

    @Test
    fun `isAllowed returns true after cooldown period expires`() {
        // Store an old transition (70 seconds ago, beyond 60s cooldown)
        val cooldownMap = JSONObject().apply {
            put("$geofenceId:Entered", TIME - 70_000)
        }
        Registry.dataStore.store("geofence_cooldowns", cooldownMap.toString())

        // Should allow transition after cooldown expires
        assertTrue(tracker.isAllowed(geofenceId, KlaviyoGeofenceTransition.Entered))
    }

    @Test
    fun `recordTransition stores timestamp in map`() {
        // Clear any existing data
        Registry.dataStore.clear("geofence_cooldowns")

        // Record a transition
        tracker.recordTransition(geofenceId, KlaviyoGeofenceTransition.Entered)

        // Verify timestamp was stored
        val storedJson = Registry.dataStore.fetch("geofence_cooldowns")
        assertNotNull(storedJson)
        val cooldownMap = JSONObject(storedJson)
        assertEquals(TIME, cooldownMap.getLong("$geofenceId:Entered"))
    }

    @Test
    fun `tracker enforces independent cooldown per geofence`() {
        val geofence1 = "geofence_1"
        val geofence2 = "geofence_2"

        // Store recent transition for geofence1 only (30 seconds ago)
        val cooldownMap = JSONObject().apply {
            put("$geofence1:Entered", TIME - 30_000)
        }
        Registry.dataStore.store("geofence_cooldowns", cooldownMap.toString())

        // geofence1 should be blocked
        assertFalse(tracker.isAllowed(geofence1, KlaviyoGeofenceTransition.Entered))

        // geofence2 should be allowed (different geofence)
        assertTrue(tracker.isAllowed(geofence2, KlaviyoGeofenceTransition.Entered))
    }

    @Test
    fun `tracker enforces independent cooldown per transition type`() {
        // Store recent ENTER transition (30 seconds ago)
        val cooldownMap = JSONObject().apply {
            put("$geofenceId:Entered", TIME - 30_000)
        }
        Registry.dataStore.store("geofence_cooldowns", cooldownMap.toString())

        // ENTER should be blocked
        assertFalse(tracker.isAllowed(geofenceId, KlaviyoGeofenceTransition.Entered))

        // EXIT should be allowed (different transition type)
        assertTrue(tracker.isAllowed(geofenceId, KlaviyoGeofenceTransition.Exited))
    }

    @Test
    fun `loadCooldownMap cleans up stale entries automatically`() {
        val geofence1 = "geofence_1"
        val geofence2 = "geofence_2"
        val geofence3 = "geofence_3"

        // Store map with both recent and stale entries
        val cooldownMap = JSONObject().apply {
            put("$geofence1:Entered", TIME - 30_000) // Recent (within 60s)
            put("$geofence2:Entered", TIME - 70_000) // Stale (beyond 60s)
            put("$geofence3:Exited", TIME - 10_000) // Recent
        }
        Registry.dataStore.store("geofence_cooldowns", cooldownMap.toString())

        // Trigger a load by calling isAllowed - this should automatically clean and persist
        tracker.isAllowed(geofence1, KlaviyoGeofenceTransition.Entered)

        // Verify the cleaned map was automatically persisted to storage
        val storedJson = Registry.dataStore.fetch("geofence_cooldowns")
        assertNotNull(storedJson)
        val cleanedMap = JSONObject(storedJson)

        // Recent entries should still exist
        assertTrue(cleanedMap.has("$geofence1:Entered"))
        assertTrue(cleanedMap.has("$geofence3:Exited"))

        // Stale entry should be gone and the cleaned map persisted
        assertFalse(cleanedMap.has("$geofence2:Entered"))

        // Verify we only have 2 entries now (the 2 recent ones)
        assertEquals(2, cleanedMap.length())
    }

    @Test
    fun `clean removes stale entries from storage`() {
        val geofence1 = "geofence_1"
        val geofence2 = "geofence_2"
        val geofence3 = "geofence_3"
        val geofence4 = "geofence_4"

        // Store map with multiple stale entries and one recent entry
        val cooldownMap = JSONObject().apply {
            put("$geofence1:Entered", TIME - 90_000) // Stale (beyond 60s)
            put("$geofence2:Exited", TIME - 120_000) // Very stale
            put("$geofence3:Dwelt", TIME - 45_000) // Recent (within 60s)
            put("$geofence4:Entered", TIME - 75_000) // Stale
        }
        Registry.dataStore.store("geofence_cooldowns", cooldownMap.toString())

        // Call clean to trigger cleanup
        tracker.clean()

        // Verify only recent entries remain in storage
        val storedJson = Registry.dataStore.fetch("geofence_cooldowns")
        assertNotNull(storedJson)
        val cleanedMap = JSONObject(storedJson)

        // Only the recent entry should exist
        assertTrue(cleanedMap.has("$geofence3:Dwelt"))

        // All stale entries should be gone
        assertFalse(cleanedMap.has("$geofence1:Entered"))
        assertFalse(cleanedMap.has("$geofence2:Exited"))
        assertFalse(cleanedMap.has("$geofence4:Entered"))

        // Verify we only have 1 entry now
        assertEquals(1, cleanedMap.length())
    }

    @Test
    fun `tracker handles missing cooldown map gracefully`() {
        // Intentionally clear the map
        Registry.dataStore.clear("geofence_cooldowns")

        // Should not throw and should allow transition
        assertTrue(tracker.isAllowed(geofenceId, KlaviyoGeofenceTransition.Entered))
    }

    @Test
    fun `tracker handles invalid JSON gracefully`() {
        // Store invalid JSON
        Registry.dataStore.store("geofence_cooldowns", "invalid-json")

        // Should not throw and should allow transition
        assertTrue(tracker.isAllowed(geofenceId, KlaviyoGeofenceTransition.Entered))
    }

    @Test
    fun `recordTransition updates existing map without losing other entries`() {
        val geofence1 = "geofence_1"
        val geofence2 = "geofence_2"

        // Store initial entry for geofence1
        val initialMap = JSONObject().apply {
            put("$geofence1:Entered", TIME - 30_000)
        }
        Registry.dataStore.store("geofence_cooldowns", initialMap.toString())

        // Record a new transition for geofence2
        tracker.recordTransition(geofence2, KlaviyoGeofenceTransition.Exited)

        // Verify both entries exist
        val storedJson = Registry.dataStore.fetch("geofence_cooldowns")
        assertNotNull(storedJson)
        val cooldownMap = JSONObject(storedJson)
        assertTrue(cooldownMap.has("$geofence1:Entered"))
        assertTrue(cooldownMap.has("$geofence2:Exited"))
        assertEquals(TIME - 30_000, cooldownMap.getLong("$geofence1:Entered"))
        assertEquals(TIME, cooldownMap.getLong("$geofence2:Exited"))
    }
}

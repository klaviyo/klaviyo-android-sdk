package com.klaviyo.location

import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class KlaviyoGeofenceTransitionTest : BaseTest() {

    @Test
    fun `enum has expected values`() {
        KlaviyoGeofenceTransition.entries.toTypedArray().apply {
            assertEquals(3, size)
            assert(contains(KlaviyoGeofenceTransition.Entered))
            assert(contains(KlaviyoGeofenceTransition.Exited))
            assert(contains(KlaviyoGeofenceTransition.Dwelt))
        }
    }

    @Test
    fun `converts GEOFENCE_TRANSITION_ENTER to Entered`() {
        val event = mockk<GeofencingEvent>().apply {
            every { geofenceTransition } returns Geofence.GEOFENCE_TRANSITION_ENTER
        }

        val result = event.toKlaviyoGeofenceEvent()

        assertEquals(KlaviyoGeofenceTransition.Entered, result)
    }

    @Test
    fun `converts GEOFENCE_TRANSITION_EXIT to Exited`() {
        val event = mockk<GeofencingEvent>().apply {
            every { geofenceTransition } returns Geofence.GEOFENCE_TRANSITION_EXIT
        }

        val result = event.toKlaviyoGeofenceEvent()

        assertEquals(KlaviyoGeofenceTransition.Exited, result)
    }

    @Test
    fun `converts GEOFENCE_TRANSITION_DWELL to Dwelt`() {
        val event = mockk<GeofencingEvent>().apply {
            every { geofenceTransition } returns Geofence.GEOFENCE_TRANSITION_DWELL
        }

        val result = event.toKlaviyoGeofenceEvent()

        assertEquals(KlaviyoGeofenceTransition.Dwelt, result)
    }

    @Test
    fun `returns null for unknown transition type`() {
        val event = mockk<GeofencingEvent>().apply {
            every { geofenceTransition } returns 999 // Unknown transition
        }

        val result = event.toKlaviyoGeofenceEvent()

        assertNull(result)
    }

    @Test
    fun `enum values have expected names`() {
        assertEquals("Entered", KlaviyoGeofenceTransition.Entered.name)
        assertEquals("Exited", KlaviyoGeofenceTransition.Exited.name)
        assertEquals("Dwelt", KlaviyoGeofenceTransition.Dwelt.name)
    }
}

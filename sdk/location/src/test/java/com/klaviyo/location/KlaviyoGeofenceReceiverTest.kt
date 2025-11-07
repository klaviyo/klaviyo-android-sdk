package com.klaviyo.location

import android.content.BroadcastReceiver
import android.content.Intent
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/**
 * Tests for KlaviyoGeofenceReceiver
 *
 * These tests verify that the geofence receiver properly delegates to LocationManager's
 * handleGeofenceIntent method with the correct context, intent, and PendingResult.
 */
internal class KlaviyoGeofenceReceiverTest : BaseTest() {

    private val mockIntent = mockk<Intent>(relaxed = true)
    private val mockLocationManager = mockk<LocationManager>(relaxed = true)
    private val mockPendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
    private val receiver = spyk(KlaviyoGeofenceReceiver()).apply {
        every { goAsync() } returns mockPendingResult
    }

    @Before
    override fun setup() {
        super.setup()

        // Register mock in Registry
        Registry.register<LocationManager> { mockLocationManager }

        // Setup context to return itself as applicationContext
        every { mockContext.applicationContext } returns mockContext
    }

    @Test
    fun `onReceive delegates to LocationManager handleGeofenceIntent async`() {
        // Trigger the receiver
        receiver.onReceive(mockContext, mockIntent)

        // Verify delegation with correct parameters
        verify(exactly = 1) {
            mockLocationManager.handleGeofenceIntent(
                mockContext,
                mockIntent,
                mockPendingResult
            )
        }

        // Verify goAsync was called
        verify(exactly = 1) { receiver.goAsync() }
    }

    @Test
    fun `onReceive handles any exceptions to avoid a crash`() {
        every { mockLocationManager.handleGeofenceIntent(any(), any(), any()) } throws Exception()

        // Trigger the receiver
        receiver.onReceive(mockContext, mockIntent)

        verify(exactly = 1) {
            mockLocationManager.handleGeofenceIntent(
                mockContext,
                mockIntent,
                mockPendingResult
            )
        }
    }
}

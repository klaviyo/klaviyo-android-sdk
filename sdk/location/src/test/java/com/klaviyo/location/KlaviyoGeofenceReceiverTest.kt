package com.klaviyo.location

import android.content.BroadcastReceiver
import android.content.Context
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
    }

    @Test
    fun `onReceive delegates to LocationManager handleGeofenceIntent`() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockIntent = mockk<Intent>(relaxed = true)

        // Setup context to return itself as applicationContext
        every { mockContext.applicationContext } returns mockContext

        // Trigger the receiver
        receiver.onReceive(mockContext, mockIntent)

        // Verify delegation with correct parameters
        verify(exactly = 1) {
            mockLocationManager.handleGeofenceIntent(
                mockContext, // applicationContext
                mockIntent,
                mockPendingResult
            )
        }
    }

    @Test
    fun `onReceive calls goAsync to extend receiver lifecycle`() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockIntent = mockk<Intent>(relaxed = true)

        every { mockContext.applicationContext } returns mockContext

        // Trigger the receiver
        receiver.onReceive(mockContext, mockIntent)

        // Verify goAsync was called
        verify(exactly = 1) { receiver.goAsync() }
    }

    @Test
    fun `onReceive uses applicationContext not activity context`() {
        val mockActivityContext = mockk<Context>(relaxed = true)
        val mockApplicationContext = mockk<Context>(relaxed = true)
        val mockIntent = mockk<Intent>(relaxed = true)

        // Setup context to return different applicationContext
        every { mockActivityContext.applicationContext } returns mockApplicationContext

        // Trigger the receiver
        receiver.onReceive(mockActivityContext, mockIntent)

        // Verify handleGeofenceIntent was called with applicationContext, not activity context
        verify(exactly = 1) {
            mockLocationManager.handleGeofenceIntent(
                mockApplicationContext, // Should use applicationContext
                mockIntent,
                mockPendingResult
            )
        }

        // Verify it was NOT called with activity context
        verify(exactly = 0) {
            mockLocationManager.handleGeofenceIntent(
                mockActivityContext,
                any(),
                any()
            )
        }
    }
}

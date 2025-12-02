package com.klaviyo.location

import android.content.Intent
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/**
 * Tests for KlaviyoGeofenceReceiver
 *
 * These tests verify that the geofence receiver properly delegates to LocationManager's
 * handleGeofenceIntent method with the correct context and intent.
 */
internal class KlaviyoGeofenceReceiverTest : BaseTest() {

    private val mockIntent = mockk<Intent>(relaxed = true)
    private val mockLocationManager = mockk<LocationManager>(relaxed = true)
    private val receiver = KlaviyoGeofenceReceiver()

    @Before
    override fun setup() {
        super.setup()

        // Register mock in Registry
        Registry.register<LocationManager> { mockLocationManager }

        // Setup context to return itself as applicationContext
        every { mockContext.applicationContext } returns mockContext
    }

    @Test
    fun `onReceive delegates to LocationManager handleGeofenceIntent`() {
        // Trigger the receiver
        receiver.onReceive(mockContext, mockIntent)

        // Verify delegation with correct parameters
        verify(exactly = 1) {
            mockLocationManager.handleGeofenceIntent(
                mockContext,
                mockIntent
            )
        }
    }

    @Test
    fun `onReceive handles any exceptions to avoid a crash`() {
        every { mockLocationManager.handleGeofenceIntent(any(), any()) } throws Exception()

        // Trigger the receiver
        receiver.onReceive(mockContext, mockIntent)

        verify(exactly = 1) {
            mockLocationManager.handleGeofenceIntent(
                mockContext,
                mockIntent
            )
        }
    }
}

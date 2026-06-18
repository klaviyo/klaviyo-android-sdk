package com.klaviyo.location

import android.content.Context
import android.content.Intent
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/**
 * Tests for KlaviyoBootReceiver
 *
 * These tests verify that the boot receiver properly validates the intent action
 * and delegates to LocationManager's handleBootEvent method when appropriate.
 */
internal class KlaviyoBootReceiverTest : BaseTest() {

    private val mockLocationManager = mockk<LocationManager>(relaxed = true)
    private val receiver = KlaviyoLocationBootReceiver()

    @Before
    override fun setup() {
        super.setup()

        // Register mock in Registry
        Registry.register<LocationManager> { mockLocationManager }
    }

    @Test
    fun `onReceive delegates to LocationManager when action is BOOT_COMPLETED`() {
        val context = mockk<Context>(relaxed = true)
        val mockIntent = mockk<Intent>(relaxed = true).apply {
            every { action } returns Intent.ACTION_BOOT_COMPLETED
        }

        every { mockLocationManager.restoreGeofencesOnBoot(any()) } just runs

        // Trigger the receiver
        receiver.onReceive(context, mockIntent)

        // Verify delegation
        verify(exactly = 1) {
            mockLocationManager.restoreGeofencesOnBoot(context)
        }
    }

    @Test
    fun `onReceive does NOT delegate when action is not BOOT_COMPLETED`() {
        val context = mockk<Context>(relaxed = true)
        val mockIntent = mockk<Intent>(relaxed = true).apply {
            every { action } returns "android.intent.action.SOME_OTHER_ACTION"
        }

        every { mockLocationManager.restoreGeofencesOnBoot(any()) } just runs

        // Trigger the receiver with wrong action
        receiver.onReceive(context, mockIntent)

        // Verify handleBootEvent was NOT called
        verify(exactly = 0) {
            mockLocationManager.restoreGeofencesOnBoot(any())
        }
    }

    @Test
    fun `onReceive does NOT delegate when action is null`() {
        val context = mockk<Context>(relaxed = true)
        val mockIntent = mockk<Intent>(relaxed = true).apply {
            every { action } returns null
        }

        every { mockLocationManager.restoreGeofencesOnBoot(any()) } just runs

        // Trigger the receiver with null action
        receiver.onReceive(context, mockIntent)

        // Verify handleBootEvent was NOT called (security check prevents spoofing)
        verify(exactly = 0) {
            mockLocationManager.restoreGeofencesOnBoot(any())
        }
    }

    @Test
    fun `onReceive does NOT crash when restoreGeofencesOnBoot throws`() {
        val context = mockk<Context>(relaxed = true)
        val mockIntent = mockk<Intent>(relaxed = true).apply {
            every { action } returns Intent.ACTION_BOOT_COMPLETED
        }

        every { mockLocationManager.restoreGeofencesOnBoot(any()) } throws Exception()

        // Trigger the receiver
        receiver.onReceive(context, mockIntent)

        // Verify delegation without throwing
        verify(exactly = 1) {
            mockLocationManager.restoreGeofencesOnBoot(context)
        }
    }
}

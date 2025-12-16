package com.klaviyo.location

import com.klaviyo.analytics.Klaviyo
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify

/**
 * Utility class for mocking Geofencing extension functions from Java tests.
 *
 * Since registerGeofencing and unregisterGeofencing are Kotlin extension functions,
 * they compile to static methods in GeofencingKt. This class provides a bridge for
 * Java tests to mock and verify these method calls.
 */
object GeofencingMock {

    /**
     * Sets up mocks for Geofencing extension functions.
     * Call this in @Before setup methods.
     */
    @JvmStatic
    fun setup() {
        // Mock the extension functions by referencing them directly
        mockkStatic(Klaviyo::registerGeofencing)
        mockkStatic(Klaviyo::unregisterGeofencing)

        every { any<Klaviyo>().registerGeofencing() } returns Klaviyo
        every { any<Klaviyo>().unregisterGeofencing() } returns Klaviyo
    }

    /**
     * Cleans up mocks.
     * Call this in @After teardown methods.
     */
    @JvmStatic
    fun teardown() {
        unmockkStatic(Klaviyo::registerGeofencing)
        unmockkStatic(Klaviyo::unregisterGeofencing)
    }

    @JvmStatic
    fun verifyRegisterGeofencingCalled() {
        verify { any<Klaviyo>().registerGeofencing() }
    }

    @JvmStatic
    fun verifyUnregisterGeofencingCalled() {
        verify { any<Klaviyo>().unregisterGeofencing() }
    }
}

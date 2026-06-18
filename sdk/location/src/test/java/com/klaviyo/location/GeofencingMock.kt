package com.klaviyo.location

import com.klaviyo.analytics.Klaviyo
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
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
     * Sets up mocks for Geofencing extension functions and KlaviyoLocation static API.
     * Call this in @Before setup methods.
     */
    @JvmStatic
    fun setup() {
        // Mock the extension functions by referencing them directly
        mockkStatic(Klaviyo::registerGeofencing)
        mockkStatic(Klaviyo::unregisterGeofencing)

        every { any<Klaviyo>().registerGeofencing() } returns Klaviyo
        every { any<Klaviyo>().unregisterGeofencing() } returns Klaviyo

        // Mock KlaviyoLocation static API (returns Unit)
        mockkStatic(KlaviyoLocation::class)
        mockkObject(KlaviyoLocation)

        every { KlaviyoLocation.registerGeofencing() } just Runs
        every { KlaviyoLocation.unregisterGeofencing() } just Runs
    }

    /**
     * Cleans up mocks.
     * Call this in @After teardown methods.
     */
    @JvmStatic
    fun teardown() {
        unmockkStatic(Klaviyo::registerGeofencing)
        unmockkStatic(Klaviyo::unregisterGeofencing)
        unmockkObject(KlaviyoLocation)
        unmockkStatic(KlaviyoLocation::class)
    }

    @JvmStatic
    @JvmOverloads
    fun verifyRegisterGeofencingCalled(count: Int = 1) {
        verify(exactly = count) { any<Klaviyo>().registerGeofencing() }
    }

    @JvmStatic
    @JvmOverloads
    fun verifyUnregisterGeofencingCalled(count: Int = 1) {
        verify(exactly = count) { any<Klaviyo>().unregisterGeofencing() }
    }

    @JvmStatic
    @JvmOverloads
    fun verifyKlaviyoLocationRegisterCalled(count: Int = 1) {
        verify(exactly = count) { KlaviyoLocation.registerGeofencing() }
    }

    @JvmStatic
    @JvmOverloads
    fun verifyKlaviyoLocationUnregisterCalled(count: Int = 1) {
        verify(exactly = count) { KlaviyoLocation.unregisterGeofencing() }
    }
}

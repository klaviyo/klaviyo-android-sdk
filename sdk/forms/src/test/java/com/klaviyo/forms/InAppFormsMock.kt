package com.klaviyo.forms

import com.klaviyo.analytics.Klaviyo
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify

/**
 * Utility class for mocking In-App Forms extension functions from Java tests.
 *
 * Since registerForInAppForms and unregisterFromInAppForms are Kotlin extension functions,
 * they compile to static methods in InAppFormsKt. This class provides a bridge for
 * Java tests to mock and verify these method calls.
 */
object InAppFormsMock {

    /**
     * Creates an InAppFormsConfig with default session timeout.
     * Helper for Java tests since Duration parameters are awkward from Java.
     */
    @JvmStatic
    fun createDefaultConfig(): InAppFormsConfig = InAppFormsConfig()

    /**
     * Sets up mocks for In-App Forms extension functions.
     * Call this in @Before setup methods.
     */
    @JvmStatic
    fun setup() {
        // Mock the extension functions by referencing them directly
        mockkStatic(Klaviyo::registerForInAppForms)
        mockkStatic(Klaviyo::unregisterFromInAppForms)

        every { any<Klaviyo>().registerForInAppForms(any()) } returns Klaviyo
        every { any<Klaviyo>().unregisterFromInAppForms() } returns Klaviyo
    }

    /**
     * Cleans up mocks.
     * Call this in @After teardown methods.
     */
    @JvmStatic
    fun teardown() {
        unmockkStatic(Klaviyo::registerForInAppForms)
        unmockkStatic(Klaviyo::unregisterFromInAppForms)
    }

    @JvmStatic
    fun verifyRegisterForInAppFormsCalled() {
        verify { any<Klaviyo>().registerForInAppForms(any()) }
    }

    @JvmStatic
    fun verifyUnregisterFromInAppFormsCalled() {
        verify { any<Klaviyo>().unregisterFromInAppForms() }
    }
}

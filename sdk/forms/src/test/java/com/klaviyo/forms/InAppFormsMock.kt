package com.klaviyo.forms

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
 * Utility class for mocking In-App Forms extension functions from Java tests.
 *
 * Since registerForInAppForms and unregisterFromInAppForms are Kotlin extension functions,
 * they compile to static methods in InAppFormsKt. This class provides a bridge for
 * Java tests to mock and verify these method calls.
 */
object InAppFormsMock {

    /**
     * Sets up mocks for In-App Forms extension functions and KlaviyoForms static API.
     * Call this in @Before setup methods.
     */
    @JvmStatic
    fun setup() {
        // Mock the extension functions by referencing them directly
        mockkStatic(Klaviyo::registerForInAppForms)
        mockkStatic(Klaviyo::unregisterFromInAppForms)

        every { any<Klaviyo>().registerForInAppForms(any()) } returns Klaviyo
        every { any<Klaviyo>().unregisterFromInAppForms() } returns Klaviyo

        // Mock KlaviyoForms static API (returns Unit)
        mockkStatic(KlaviyoForms::class)
        mockkObject(KlaviyoForms)

        every { KlaviyoForms.registerForInAppForms(any()) } just Runs
        every { KlaviyoForms.registerForInAppForms() } just Runs
        every { KlaviyoForms.unregisterFromInAppForms() } just Runs
    }

    /**
     * Cleans up mocks.
     * Call this in @After teardown methods.
     */
    @JvmStatic
    fun teardown() {
        unmockkStatic(Klaviyo::registerForInAppForms)
        unmockkStatic(Klaviyo::unregisterFromInAppForms)
        unmockkObject(KlaviyoForms)
        unmockkStatic(KlaviyoForms::class)
    }

    @JvmStatic
    @JvmOverloads
    fun verifyRegisterForInAppFormsCalled(count: Int = 1) {
        verify(exactly = count) { any<Klaviyo>().registerForInAppForms(any()) }
    }

    @JvmStatic
    @JvmOverloads
    fun verifyUnregisterFromInAppFormsCalled(count: Int = 1) {
        verify(exactly = count) { any<Klaviyo>().unregisterFromInAppForms() }
    }

    @JvmStatic
    @JvmOverloads
    fun verifyKlaviyoFormsRegisterCalled(count: Int = 1) {
        verify(exactly = count) { KlaviyoForms.registerForInAppForms(any()) }
    }

    @JvmStatic
    @JvmOverloads
    fun verifyKlaviyoFormsRegisterCalledNoArg(count: Int = 1) {
        verify(exactly = count) { KlaviyoForms.registerForInAppForms() }
    }

    @JvmStatic
    @JvmOverloads
    fun verifyKlaviyoFormsUnregisterCalled(count: Int = 1) {
        verify(exactly = count) { KlaviyoForms.unregisterFromInAppForms() }
    }
}

package com.klaviyo.forms

import com.klaviyo.analytics.Klaviyo
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
     * Creates an InAppFormsConfig with a custom session timeout in seconds.
     * Helper for Java tests since kotlin.time.Duration is not easily created from Java.
     *
     * @param seconds Session timeout duration in seconds
     */
    @JvmStatic
    fun createConfigWithTimeoutSeconds(timeoutSeconds: Long): InAppFormsConfig =
        InAppFormsConfig(timeoutSeconds.seconds)

    /**
     * Creates an InAppFormsConfig with infinite session timeout (never times out).
     * Helper for Java tests.
     */
    @JvmStatic
    fun createConfigWithInfiniteTimeout(): InAppFormsConfig =
        InAppFormsConfig(Duration.INFINITE)

    /**
     * Creates an InAppFormsConfig with zero timeout (immediate timeout on background).
     * Helper for Java tests.
     */
    @JvmStatic
    fun createConfigWithZeroTimeout(): InAppFormsConfig =
        InAppFormsConfig(Duration.ZERO)

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

        // Mock KlaviyoForms static API
        mockkStatic(KlaviyoForms::class)
        mockkObject(KlaviyoForms)

        every { KlaviyoForms.registerForInAppForms(any()) } returns Klaviyo
        every { KlaviyoForms.registerForInAppForms() } returns Klaviyo
        every { KlaviyoForms.unregisterFromInAppForms() } returns Klaviyo
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
    fun verifyRegisterForInAppFormsCalled() {
        verify { any<Klaviyo>().registerForInAppForms(any()) }
    }

    @JvmStatic
    fun verifyUnregisterFromInAppFormsCalled() {
        verify { any<Klaviyo>().unregisterFromInAppForms() }
    }

    @JvmStatic
    fun verifyKlaviyoFormsRegisterCalled() {
        verify { KlaviyoForms.registerForInAppForms(any()) }
    }

    @JvmStatic
    fun verifyKlaviyoFormsRegisterCalledNoArg() {
        verify { KlaviyoForms.registerForInAppForms() }
    }

    @JvmStatic
    fun verifyKlaviyoFormsUnregisterCalled() {
        verify { KlaviyoForms.unregisterFromInAppForms() }
    }
}

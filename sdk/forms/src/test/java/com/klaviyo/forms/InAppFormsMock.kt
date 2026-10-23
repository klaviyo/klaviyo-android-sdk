package com.klaviyo.forms

import android.net.Uri
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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
        mockkStatic(Klaviyo::setFormsPreviewToken)

        every { any<Klaviyo>().registerForInAppForms(any()) } returns Klaviyo
        every { any<Klaviyo>().unregisterFromInAppForms() } returns Klaviyo
        every { any<Klaviyo>().setFormsPreviewToken(any()) } returns Klaviyo

        // Mock KlaviyoForms static API (returns Unit)
        mockkStatic(KlaviyoForms::class)
        mockkObject(KlaviyoForms)

        every { KlaviyoForms.registerForInAppForms(any()) } just Runs
        every { KlaviyoForms.registerForInAppForms() } just Runs
        every { KlaviyoForms.unregisterFromInAppForms() } just Runs
        every { KlaviyoForms.setFormsPreviewToken(any()) } just Runs
    }

    /**
     * Cleans up mocks.
     * Call this in @After teardown methods.
     */
    @JvmStatic
    fun teardown() {
        unmockkStatic(Klaviyo::registerForInAppForms)
        unmockkStatic(Klaviyo::unregisterFromInAppForms)
        unmockkStatic(Klaviyo::setFormsPreviewToken)
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

    @JvmStatic
    @JvmOverloads
    fun verifySetFormsPreviewTokenCalled(count: Int = 1) {
        verify(exactly = count) { any<Klaviyo>().setFormsPreviewToken(any()) }
    }

    @JvmStatic
    @JvmOverloads
    fun verifyKlaviyoFormsSetPreviewTokenCalled(count: Int = 1) {
        verify(exactly = count) { KlaviyoForms.setFormsPreviewToken(any()) }
    }

    private var previewLinkProvider: FormsProvider? = null

    /**
     * Restore the real [KlaviyoForms] facade, so a test exercises its forwarding to [Klaviyo].
     */
    @JvmStatic
    fun unmockKlaviyoForms() {
        unmockkObject(KlaviyoForms)
        unmockkStatic(KlaviyoForms::class)
    }

    /**
     * Register a [FormsProvider] whose `handlePreviewLink` returns [result] for [link] and false
     * for any other link.
     */
    @JvmStatic
    fun registerPreviewLinkProvider(link: String, result: Boolean) {
        val provider = mockk<FormsProvider>(relaxed = true).apply {
            every { handlePreviewLink(any()) } answers { firstArg<String?>() == link && result }
        }
        previewLinkProvider = provider
        Registry.register<FormsProvider>(provider)
    }

    @JvmStatic
    fun unregisterPreviewLinkProvider() {
        Registry.unregister<FormsProvider>()
        previewLinkProvider = null
    }

    @JvmStatic
    fun verifyPreviewLinkForwarded(link: String) {
        val provider = requireNotNull(previewLinkProvider)
        verify(exactly = 1) { provider.handlePreviewLink(link) }
    }

    /**
     * A [Uri] whose string form is [value], since `Uri.parse` is a stub in JVM unit tests.
     */
    @JvmStatic
    fun uri(value: String): Uri = mockk<Uri>().also { every { it.toString() } returns value }
}

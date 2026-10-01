package com.klaviyo.inbox

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
 * Mocks the Mobile Inbox registration extension functions and [KlaviyoInbox] for Java tests.
 */
object MobileInboxMock {

    @JvmStatic
    fun setup() {
        mockkStatic(Klaviyo::registerForMobileInbox)
        mockkStatic(Klaviyo::unregisterFromMobileInbox)

        every { any<Klaviyo>().registerForMobileInbox(any()) } returns Klaviyo
        every { any<Klaviyo>().unregisterFromMobileInbox() } returns Klaviyo

        mockkStatic(KlaviyoInbox::class)
        mockkObject(KlaviyoInbox)
        every { KlaviyoInbox.registerForMobileInbox(any()) } just Runs
        every { KlaviyoInbox.registerForMobileInbox() } just Runs
        every { KlaviyoInbox.unregisterFromMobileInbox() } just Runs
    }

    @JvmStatic
    fun teardown() {
        unmockkStatic(Klaviyo::registerForMobileInbox)
        unmockkStatic(Klaviyo::unregisterFromMobileInbox)
        unmockkObject(KlaviyoInbox)
        unmockkStatic(KlaviyoInbox::class)
    }

    @JvmStatic
    @JvmOverloads
    fun verifyRegisterCalled(config: MobileInboxConfig? = null) {
        verify(exactly = 1) {
            any<Klaviyo>().registerForMobileInbox(config ?: any())
        }
    }

    @JvmStatic
    fun verifyUnregisterCalled() {
        verify(exactly = 1) { any<Klaviyo>().unregisterFromMobileInbox() }
    }

    @JvmStatic
    fun verifyKlaviyoInboxRegisterCalled() {
        verify(exactly = 1) { KlaviyoInbox.registerForMobileInbox(any()) }
    }

    @JvmStatic
    fun verifyKlaviyoInboxUnregisterCalled() {
        verify(exactly = 1) { KlaviyoInbox.unregisterFromMobileInbox() }
    }
}

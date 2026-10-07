package com.klaviyo.inbox

import com.klaviyo.analytics.Klaviyo
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify

/**
 * Mocks the Mobile Inbox registration extension functions for Java tests.
 * [KlaviyoInbox] is left real, so tests also cover its delegation to the extensions.
 */
object MobileInboxMock {

    @JvmStatic
    fun setup() {
        mockkStatic(Klaviyo::registerForMobileInbox)
        mockkStatic(Klaviyo::unregisterFromMobileInbox)

        every { any<Klaviyo>().registerForMobileInbox(any()) } returns Klaviyo
        every { any<Klaviyo>().unregisterFromMobileInbox() } returns Klaviyo
    }

    @JvmStatic
    fun teardown() {
        unmockkStatic(Klaviyo::registerForMobileInbox)
        unmockkStatic(Klaviyo::unregisterFromMobileInbox)
    }

    @JvmStatic
    fun verifyRegisterCalled(config: MobileInboxConfig) {
        verify(exactly = 1) { any<Klaviyo>().registerForMobileInbox(config) }
    }

    @JvmStatic
    fun verifyUnregisterCalled() {
        verify(exactly = 1) { any<Klaviyo>().unregisterFromMobileInbox() }
    }
}

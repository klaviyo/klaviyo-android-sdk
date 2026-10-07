package com.klaviyo.inbox

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.fixtures.BaseTest
import org.junit.Assert.assertThrows
import org.junit.Test

internal class MobileInboxNoOpTest : BaseTest() {
    @Test
    fun `registerForMobileInbox throws when provider not registered`() {
        assertThrows(MissingKlaviyoModule::class.java) {
            Klaviyo.registerForMobileInbox()
        }
    }

    @Test
    fun `unregisterFromMobileInbox throws when provider not registered`() {
        assertThrows(MissingKlaviyoModule::class.java) {
            Klaviyo.unregisterFromMobileInbox()
        }
    }

    @Test
    fun `KlaviyoInbox static methods throw when provider not registered`() {
        assertThrows(MissingKlaviyoModule::class.java) {
            KlaviyoInbox.registerForMobileInbox()
        }
        assertThrows(MissingKlaviyoModule::class.java) {
            KlaviyoInbox.unregisterFromMobileInbox()
        }
    }
}

package com.klaviyo.inbox

import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

internal class KlaviyoMobileInboxProviderTest : BaseTest() {

    @After
    override fun cleanup() {
        Registry.unregister<InboxRepository>()
        Registry.unregister<MobileInboxProvider>()
        super.cleanup()
    }

    @Test
    fun `unread count is zero before the SDK is initialized`() = runBlocking {
        every { Registry.config } throws MissingConfig()
        InboxInitProvider().onCreate()

        assertEquals(0, KlaviyoMobileInboxProvider().mobileInbox.fetchUnreadCount())
    }
}

package com.klaviyo.inbox

import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

internal class InboxInitProviderTest : BaseTest() {

    @After
    override fun cleanup() {
        Registry.unregister<MobileInboxProvider>()
        super.cleanup()
    }

    @Test
    fun `onCreate registers MobileInboxProvider`() {
        assertTrue(InboxInitProvider().onCreate())
        assertTrue(Registry.getOrNull<MobileInboxProvider>() is KlaviyoMobileInboxProvider)
    }

    @Test
    fun `onCreate does no storage work`() {
        InboxInitProvider().onCreate()
        Registry.get<MobileInboxProvider>()

        verify(exactly = 0) { spyDataStore.fetch(any()) }
        verify(exactly = 0) { spyDataStore.store(any(), any()) }
        verify(exactly = 0) { mockContext.deleteDatabase(any()) }
    }
}

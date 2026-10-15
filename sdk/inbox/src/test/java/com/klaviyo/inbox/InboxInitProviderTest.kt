package com.klaviyo.inbox

import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.pushFcm.PushNotificationDispatcher
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class InboxInitProviderTest : BaseTest() {

    private val pushDispatcher = mockk<PushNotificationDispatcher>(relaxed = true)

    @Before
    override fun setup() {
        super.setup()
        Registry.register<PushNotificationDispatcher>(pushDispatcher)
    }

    @After
    override fun cleanup() {
        Registry.unregister<PushNotificationDispatcher>()
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

    @Test
    fun `onCreate registers the capture observer with push`() {
        InboxInitProvider().onCreate()

        verify { pushDispatcher.onKlaviyoNotification(InboxCaptureObserver) }
    }

    @Test
    fun `onCreate still registers the inbox when push is unavailable`() {
        Registry.unregister<PushNotificationDispatcher>()

        assertTrue(InboxInitProvider().onCreate())
        assertTrue(Registry.getOrNull<MobileInboxProvider>() is KlaviyoMobileInboxProvider)
        verify { spyLog.warning(any(), null) }
    }
}

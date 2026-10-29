package com.klaviyo.inbox

import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.pushFcm.PushNotificationDispatcher
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertSame
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
        Registry.unregister<InboxRepository>()
        Registry.unregister<MobileInboxProvider>()
        super.cleanup()
    }

    @Test
    fun `onCreate registers the repository without opening the store`() {
        InboxInitProvider().onCreate()

        assertTrue(Registry.isRegistered<InboxRepository>())
        verify(exactly = 0) { mockConfig.applicationContext }
    }

    @Test
    fun `onCreate keeps a repository that is already registered`() {
        val existing = mockk<InboxRepository>()
        Registry.register<InboxRepository>(existing)

        InboxInitProvider().onCreate()

        assertSame(existing, Registry.getOrNull<InboxRepository>())
    }

    @Test
    fun `onCreate makes the public inbox available`() {
        InboxInitProvider().onCreate()

        assertTrue(Registry.getOrNull<MobileInboxProvider>() is KlaviyoMobileInboxProvider)
    }

    @Test
    fun `onCreate registers the capture observer with push`() {
        InboxInitProvider().onCreate()

        verify { pushDispatcher.onKlaviyoNotification(InboxCaptureObserver) }
    }

    @Test
    fun `onCreate warns when push is unavailable`() {
        Registry.unregister<PushNotificationDispatcher>()

        assertTrue(InboxInitProvider().onCreate())
        verify { spyLog.warning(any(), null) }
    }
}

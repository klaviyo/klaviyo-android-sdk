package com.klaviyo.inbox

import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.pushFcm.PushNotificationDispatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class InboxInitProviderTest : BaseTest() {

    private val pushDispatcher = mockk<PushNotificationDispatcher>(relaxed = true)

    private fun provider() = inboxInitProvider()

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
        provider().onCreate()

        assertTrue(Registry.isRegistered<InboxRepository>())
        verify(exactly = 0) { mockConfig.applicationContext }
    }

    @Test
    fun `onCreate does not read the SDK config, which is absent before initialize`() {
        every { Registry.config } throws MissingConfig()

        provider().onCreate()

        assertTrue(Registry.isRegistered<InboxRepository>())
        verify { pushDispatcher.onKlaviyoNotification(any<InboxCaptureObserver>()) }
        verify(exactly = 0) { spyLog.error(any(), any<Throwable>()) }
    }

    @Test
    fun `onCreate leaves capture off and logs when there is no context`() {
        assertTrue(inboxInitProvider(context = null).onCreate())

        assertFalse(Registry.isRegistered<InboxRepository>())
        verify(exactly = 0) { pushDispatcher.onKlaviyoNotification(any()) }
        verify { spyLog.error(any(), null) }
    }

    @Test
    fun `onCreate keeps a repository that is already registered`() {
        val existing = mockk<InboxRepository>()
        Registry.register<InboxRepository>(existing)

        provider().onCreate()

        assertSame(existing, Registry.getOrNull<InboxRepository>())
    }

    @Test
    fun `onCreate makes the public inbox available`() {
        provider().onCreate()

        assertTrue(Registry.getOrNull<MobileInboxProvider>() is KlaviyoMobileInboxProvider)
    }

    @Test
    fun `onCreate registers the capture observer with push`() {
        provider().onCreate()

        verify { pushDispatcher.onKlaviyoNotification(any<InboxCaptureObserver>()) }
    }

    @Test
    fun `onCreate warns when push is unavailable`() {
        Registry.unregister<PushNotificationDispatcher>()

        assertTrue(provider().onCreate())
        verify { spyLog.warning(any(), null) }
    }
}

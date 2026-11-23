package com.klaviyo.inbox

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.state.State
import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

internal class KlaviyoMobileInboxProviderTest : BaseTest() {

    private val mockState = mockk<State>()
    private val mockProfile = mockk<Profile>()

    private fun registerState() {
        every { mockState.getAsProfile() } returns mockProfile
        every { mockState.createEvent(any(), any()) } answers { firstArg() }
        Registry.register<State> { mockState }
    }

    @After
    override fun cleanup() {
        Registry.unregister<InboxRepository>()
        Registry.unregister<State>()
        Registry.unregister<MobileInboxProvider>()
        super.cleanup()
    }

    @Test
    fun `unread count is zero before the SDK is initialized`() = runBlocking {
        every { Registry.config } throws MissingConfig()
        inboxInitProvider().onCreate()

        assertEquals(0, KlaviyoMobileInboxProvider().mobileInbox.fetchUnreadCount())
    }

    @Test
    fun `a click is enqueued as a branded event without touching the repository`() {
        val repository = mockk<InboxRepository>()
        Registry.register<InboxRepository>(repository)
        registerState()
        val message = inboxMessageEntity(transmissionId = "tm-7").toInboxMessage()
        val captured = slot<Event>()
        every { mockState.createEvent(capture(captured), any()) } answers { firstArg() }

        KlaviyoMobileInboxProvider().mobileInbox.trackMessageClick(message)

        assertEquals("\$clicked_app_inbox_message", captured.captured.metric.name)
        assertEquals("klaviyo", captured.captured.metric.service)
        confirmVerified(repository)
    }

    @Test
    fun `a malformed click enqueues nothing`() {
        registerState()
        val message = inboxMessageEntity(transmissionId = "").toInboxMessage()

        KlaviyoMobileInboxProvider().mobileInbox.trackMessageClick(message)

        verify(exactly = 0) { mockState.createEvent(any(), any()) }
    }

    @Test
    fun `a page is empty before the SDK is initialized`() = runBlocking {
        every { Registry.config } throws MissingConfig()
        inboxInitProvider().onCreate()

        val page = KlaviyoMobileInboxProvider().mobileInbox.fetchPage()

        assertEquals(emptyList<InboxMessage>(), page.messages)
    }
}

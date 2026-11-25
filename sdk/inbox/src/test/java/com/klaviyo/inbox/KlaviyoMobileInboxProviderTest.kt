package com.klaviyo.inbox

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChangeObservers
import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.core.model.DataStore
import com.klaviyo.fixtures.BaseTest
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        Registry.unregister<InboxResetGate>()
        Registry.unregister<MobileInboxProvider>()
        Registry.unregister<StateChangeObservers>()
        Registry.unregister<DataStore>()
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

        assertEquals("Clicked App Inbox Message", captured.captured.metric.name)
        assertEquals("klaviyo", captured.captured.metric.service)
        confirmVerified(repository)
    }

    @Test
    fun `a click by message ID looks the message up and enqueues the event`() = runBlocking {
        val repository = mockk<InboxRepository>()
        Registry.register<InboxRepository>(repository)
        registerState()
        val message = inboxMessageEntity(transmissionId = "tm-7").toInboxMessage()
        coEvery { repository.fetchMessage("tm-7") } returns message
        val captured = slot<Event>()
        every { mockState.createEvent(capture(captured), any()) } answers { firstArg() }

        assertTrue(KlaviyoMobileInboxProvider().mobileInbox.trackMessageClick("tm-7"))

        assertEquals("Clicked App Inbox Message", captured.captured.metric.name)
        assertEquals("klaviyo", captured.captured.metric.service)
    }

    @Test
    fun `a click by message ID on a button uses that button`() = runBlocking {
        val repository = mockk<InboxRepository>()
        Registry.register<InboxRepository>(repository)
        registerState()
        val message = inboxMessageEntity(
            transmissionId = "tm-7",
            actions = listOf(
                InboxRecordAction(
                    "a1",
                    "Shop now",
                    InboxRecordDestination("deep_link", "myapp://shop")
                )
            )
        ).toInboxMessage()
        val button = message.actions.first()
        coEvery { repository.fetchMessage("tm-7") } returns message
        val captured = slot<Event>()
        every { mockState.createEvent(capture(captured), any()) } answers { firstArg() }

        assertTrue(KlaviyoMobileInboxProvider().mobileInbox.trackMessageClick("tm-7", button.id))

        assertEquals(button.label, captured.captured[InboxClickProperty.BUTTON_LABEL])
    }

    @Test
    fun `a click by message ID for a missing message enqueues nothing`() = runBlocking {
        val repository = mockk<InboxRepository>()
        Registry.register<InboxRepository>(repository)
        registerState()
        coEvery { repository.fetchMessage("gone") } returns null

        assertFalse(KlaviyoMobileInboxProvider().mobileInbox.trackMessageClick("gone"))

        verify(exactly = 0) { mockState.createEvent(any(), any()) }
    }

    @Test
    fun `a click by message ID for an unknown button enqueues nothing`() = runBlocking {
        val repository = mockk<InboxRepository>()
        Registry.register<InboxRepository>(repository)
        registerState()
        val message = inboxMessageEntity(transmissionId = "tm-7").toInboxMessage()
        coEvery { repository.fetchMessage("tm-7") } returns message

        assertFalse(KlaviyoMobileInboxProvider().mobileInbox.trackMessageClick("tm-7", "nope"))

        verify(exactly = 0) { mockState.createEvent(any(), any()) }
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

    @Test
    fun `clearAll is zero before the SDK is initialized`() = runBlocking {
        every { Registry.config } throws MissingConfig()
        inboxInitProvider().onCreate()

        assertEquals(0, KlaviyoMobileInboxProvider().mobileInbox.clearAll())
    }

    @Test
    fun `changes come from the repository registered when they are collected`() = runBlocking {
        val repositoryChanges = MutableSharedFlow<Unit>(replay = 1)
        val repository = mockk<InboxRepository> {
            every { changes } returns repositoryChanges
        }
        val inbox = KlaviyoMobileInboxProvider().mobileInbox
        Registry.register<InboxRepository>(repository)
        repositoryChanges.emit(Unit)

        assertEquals(Unit, inbox.changes.first())
    }
}

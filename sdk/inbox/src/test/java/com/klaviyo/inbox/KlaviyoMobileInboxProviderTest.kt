package com.klaviyo.inbox

import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
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
        inboxInitProvider().onCreate()

        assertEquals(0, KlaviyoMobileInboxProvider().mobileInbox.fetchUnreadCount())
    }

    @Test
    fun `a page is empty before the SDK is initialized`() = runBlocking {
        every { Registry.config } throws MissingConfig()
        inboxInitProvider().onCreate()

        val page = KlaviyoMobileInboxProvider().mobileInbox.fetchPage()

        assertEquals(emptyList<InboxMessage>(), page.messages)
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

package com.klaviyo.mobileInbox

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.lang.reflect.Field
import org.junit.After
import org.junit.Before
import org.junit.Test

class KlaviyoMobileInboxTest : BaseTest() {

    @Before
    override fun setup() {
        super.setup()
        // Reset the singleton state before each test
        resetSingleton()
    }

    @After
    override fun cleanup() {
        super.cleanup()
        resetSingleton()
    }

    private fun resetSingleton() {
        val field: Field = KlaviyoMobileInbox::class.java.getDeclaredField("repository")
        field.isAccessible = true
        field.set(KlaviyoMobileInbox, null)
    }

    @Test
    fun `handlePushMessage before initialize logs warning`() {
        val message = buildMockMessage(messageId = "msg-1", title = "T", body = "B")
        KlaviyoMobileInbox.handlePushMessage(message)
        verify { spyLog.warning(match { it.contains("before initialize") }) }
    }

    @Test
    fun `sync before initialize logs warning`() = kotlinx.coroutines.test.runTest {
        KlaviyoMobileInbox.sync()
        verify { spyLog.warning(match { it.contains("before initialize") }) }
    }

    @Test
    fun `markRead before initialize logs warning`() = kotlinx.coroutines.test.runTest {
        KlaviyoMobileInbox.markRead("msg-1")
        verify { spyLog.warning(match { it.contains("before initialize") }) }
    }

    @Test
    fun `markHidden before initialize logs warning`() = kotlinx.coroutines.test.runTest {
        KlaviyoMobileInbox.markHidden("msg-1")
        verify { spyLog.warning(match { it.contains("before initialize") }) }
    }

    @Test
    fun `handlePushMessage with null messageId generates fallback and logs warning`() {
        mockkObject(InboxDatabase)
        val mockDb = mockk<InboxDatabase>(relaxed = true)
        val mockDao = mockk<InboxMessageDao>(relaxed = true)
        every { InboxDatabase.getInstance(any()) } returns mockDb
        every { mockDb.inboxMessageDao() } returns mockDao

        KlaviyoMobileInbox.initialize(mockContext, "https://inbox.example.com")

        val message = buildMockMessage(messageId = null, title = "T", body = "B")
        KlaviyoMobileInbox.handlePushMessage(message)

        verify { spyLog.warning(match { it.contains("messageId is null") }) }

        unmockkObject(InboxDatabase)
    }

    private fun buildMockMessage(
        messageId: String?,
        title: String,
        body: String
    ): RemoteMessage = mockk<RemoteMessage>().apply {
        every { this@apply.messageId } returns messageId
        every { data } returns mapOf(
            "_k" to "",
            "title" to title,
            "body" to body
        )
    }
}

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
    fun `handlePushMessage skips save when save_to_inbox flag is absent`() {
        withInitializedInbox {
            val message = buildMockMessage(
                messageId = "msg-1",
                title = "T",
                body = "B",
                saveToInbox = false
            )
            KlaviyoMobileInbox.handlePushMessage(message)
            verify { spyLog.verbose(match { it.contains("Skipping inbox storage") }) }
        }
    }

    @Test
    fun `handlePushMessage skips save when save_to_inbox is 0`() {
        withInitializedInbox {
            val message = buildMockMessage(
                messageId = "msg-1",
                title = "T",
                body = "B",
                saveToInboxValue = "0"
            )
            KlaviyoMobileInbox.handlePushMessage(message)
            verify { spyLog.verbose(match { it.contains("Skipping inbox storage") }) }
        }
    }

    @Test
    fun `handlePushMessage with null messageId and save_to_inbox 1 generates fallback and logs warning`() {
        withInitializedInbox {
            val message = buildMockMessage(
                messageId = null,
                title = "T",
                body = "B",
                saveToInbox = true
            )
            KlaviyoMobileInbox.handlePushMessage(message)
            verify { spyLog.warning(match { it.contains("messageId is null") }) }
        }
    }

    private fun withInitializedInbox(block: () -> Unit) {
        mockkObject(InboxDatabase)
        val mockDb = mockk<InboxDatabase>(relaxed = true)
        val mockDao = mockk<InboxMessageDao>(relaxed = true)
        every { InboxDatabase.getInstance(any()) } returns mockDb
        every { mockDb.inboxMessageDao() } returns mockDao
        KlaviyoMobileInbox.initialize(mockContext, "https://inbox.example.com")
        try {
            block()
        } finally {
            unmockkObject(InboxDatabase)
        }
    }

    private fun buildMockMessage(
        messageId: String?,
        title: String,
        body: String,
        saveToInbox: Boolean? = null,
        saveToInboxValue: String? = null
    ): RemoteMessage = mockk<RemoteMessage>().apply {
        every { this@apply.messageId } returns messageId
        val data = mutableMapOf("_k" to "", "title" to title, "body" to body)
        when {
            saveToInboxValue != null -> data["_klaviyo_save_to_inbox"] = saveToInboxValue
            saveToInbox == true -> data["_klaviyo_save_to_inbox"] = "1"
        }
        every { this@apply.data } returns data
    }
}

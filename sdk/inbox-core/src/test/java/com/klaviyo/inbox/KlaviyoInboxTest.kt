package com.klaviyo.inbox

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KlaviyoInboxTest : BaseTest() {

    private val inbox = FakeMobileInbox()

    private inline fun <reified T : Any> assertIs(value: Any?): T {
        assertTrue("Expected ${T::class.java.simpleName} but was $value", value is T)
        return value as T
    }

    @Before
    override fun setup() {
        super.setup()
        // Run the call inline, and the mock thread helper runs callbacks inline too
        every { Registry.dispatcher } returns Dispatchers.Unconfined
        inbox.register()
    }

    @After
    override fun cleanup() {
        inbox.unregister()
        super.cleanup()
    }

    @Test
    fun `the Kotlin entry point returns the registered inbox`() {
        assertSame(inbox, Klaviyo.mobileInbox)
    }

    @Test
    fun `the entry point throws when the inbox module is absent`() {
        inbox.unregister()

        assertThrows(MissingKlaviyoModule::class.java) { Klaviyo.mobileInbox }
    }

    @Test
    fun `the Java entry point throws when the inbox module is absent`() {
        inbox.unregister()

        assertThrows(MissingKlaviyoModule::class.java) { KlaviyoInbox.fetchUnreadCount { } }
    }

    @Test
    fun `fetchUnreadCount delivers the count`() {
        inbox.unreadCount = 4
        var result: InboxResult<Int>? = null

        KlaviyoInbox.fetchUnreadCount { result = it }

        assertEquals(4, assertIs<InboxResult.Success<Int>>(result).value)
    }

    @Test
    fun `an inbox failure reaches the callback as a failure`() {
        val failure = InboxException(InboxException.Reason.STORAGE_FAILURE, "store failed")
        inbox.failure = failure
        var result: InboxResult<Int>? = null

        KlaviyoInbox.fetchUnreadCount { result = it }

        assertSame(failure, assertIs<InboxResult.Failure>(result).exception)
    }

    @Test
    fun `an unexpected failure is logged and reported as a storage failure`() {
        val failure = IllegalStateException("boom")
        inbox.failure = failure
        var result: InboxResult<Int>? = null

        KlaviyoInbox.fetchUnreadCount { result = it }

        val error = assertIs<InboxResult.Failure>(result).exception
        assertEquals(InboxException.Reason.STORAGE_FAILURE, error.reason)
        assertSame(failure, error.cause)
        verify { spyLog.error("Mobile Inbox call failed", failure) }
    }
}

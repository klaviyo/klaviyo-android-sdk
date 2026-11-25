package com.klaviyo.inbox

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.verify
import java.io.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
    fun `fetchPage delivers the page for the query`() {
        val query = InboxQuery(pageSize = 5, cursor = InboxCursor("token"))
        var result: InboxResult<InboxPage>? = null

        KlaviyoInbox.fetchPage(query) { result = it }

        assertSame(inbox.page, assertIs<InboxResult.Success<InboxPage>>(result).value)
        assertEquals(listOf(query), inbox.queries)
    }

    @Test
    fun `trackMessageClick forwards the message, action and properties`() {
        val message = InboxMessage(
            "tm-1", null, null, InboxDestination(InboxDestinationType.OPEN_APP, null), null, null,
            emptyMap(), emptyList(), 0L, 0L, null, null, false, false, InboxMessageSource.CAPTURED
        )
        val action = InboxAction(
            "a",
            "Shop",
            InboxDestination(InboxDestinationType.DEEP_LINK, "myapp://shop")
        )

        KlaviyoInbox.trackMessageClick(message, action, mapOf("screen" to "inbox"))

        assertEquals(
            listOf(Triple(message, action, mapOf<String, Serializable?>("screen" to "inbox"))),
            inbox.clicks
        )
    }

    @Test
    fun `trackMessageClick without an action or properties defaults to none`() {
        val message = InboxMessage(
            "tm-1", null, null, InboxDestination(InboxDestinationType.OPEN_APP, null), null, null,
            emptyMap(), emptyList(), 0L, 0L, null, null, false, false, InboxMessageSource.CAPTURED
        )

        KlaviyoInbox.trackMessageClick(message)

        assertEquals(listOf(Triple(message, null, emptyMap<String, Serializable?>())), inbox.clicks)
    }

    @Test
    fun `trackMessageClick does not change message state`() {
        val message = InboxMessage(
            "tm-1", null, null, InboxDestination(InboxDestinationType.OPEN_APP, null), null, null,
            emptyMap(), emptyList(), 0L, 0L, null, null, false, false, InboxMessageSource.CAPTURED
        )

        KlaviyoInbox.trackMessageClick(message)

        assertEquals(emptyList<Pair<String, InboxStateChange>>(), inbox.stateChanges)
    }

    @Test
    fun `the Java entry point throws when the inbox module is absent for a click`() {
        inbox.unregister()
        val message = InboxMessage(
            "tm-1", null, null, InboxDestination(InboxDestinationType.OPEN_APP, null), null, null,
            emptyMap(), emptyList(), 0L, 0L, null, null, false, false, InboxMessageSource.CAPTURED
        )

        assertThrows(MissingKlaviyoModule::class.java) { KlaviyoInbox.trackMessageClick(message) }
    }

    @Test
    fun `fetchPage without a query asks for the first page`() {
        KlaviyoInbox.fetchPage { }

        assertEquals(listOf(InboxQuery()), inbox.queries)
    }

    @Test
    fun `an invalid cursor reaches the callback as a failure`() {
        val failure = InboxException.InvalidCursor("bad cursor")
        inbox.failure = failure
        var result: InboxResult<InboxPage>? = null

        KlaviyoInbox.fetchPage { result = it }

        assertSame(failure, assertIs<InboxResult.Failure>(result).exception)
    }

    @Test
    fun `the Java entry point for pages throws when the inbox module is absent`() {
        inbox.unregister()

        assertThrows(MissingKlaviyoModule::class.java) { KlaviyoInbox.fetchPage { } }
    }

    @Test
    fun `fetchUnreadCount delivers the count`() {
        inbox.unreadCount = 4
        var result: InboxResult<Int>? = null

        KlaviyoInbox.fetchUnreadCount { result = it }

        assertEquals(4, assertIs<InboxResult.Success<Int>>(result).value)
    }

    @Test
    fun `updateMessageState delivers whether the message exists`() {
        var result: InboxResult<Boolean>? = null

        KlaviyoInbox.updateMessageState("tm-1", InboxStateChange.ARCHIVE) { result = it }

        assertEquals(true, assertIs<InboxResult.Success<Boolean>>(result).value)
        assertEquals(listOf("tm-1" to InboxStateChange.ARCHIVE), inbox.stateChanges)
    }

    @Test
    fun `updateMessageState reports a missing message as false`() {
        inbox.messageExists = false
        var result: InboxResult<Boolean>? = null

        KlaviyoInbox.updateMessageState("gone", InboxStateChange.MARK_READ) { result = it }

        assertEquals(false, assertIs<InboxResult.Success<Boolean>>(result).value)
    }

    @Test
    fun `bulk updateMessageState delivers the changed count for the filter`() {
        inbox.bulkChangedCount = 3
        var result: InboxResult<Int>? = null
        val filter = InboxFilter.where(InboxReadFilter.UNREAD, InboxArchiveFilter.ACTIVE)

        KlaviyoInbox.updateMessageState(InboxStateChange.MARK_READ, filter) { result = it }

        assertEquals(3, assertIs<InboxResult.Success<Int>>(result).value)
        assertEquals(listOf(InboxStateChange.MARK_READ to filter), inbox.bulkStateChanges)
    }

    @Test
    fun `a failed bulk state update reaches the callback as a failure`() {
        val failure = InboxException.StorageFailure("store failed")
        inbox.failure = failure
        var result: InboxResult<Int>? = null

        KlaviyoInbox.updateMessageState(InboxStateChange.ARCHIVE, InboxFilter.ALL_MESSAGES) {
            result = it
        }

        assertSame(failure, assertIs<InboxResult.Failure>(result).exception)
    }

    @Test
    fun `an unexpected bulk update failure is wrapped as a storage failure`() {
        inbox.failure = IllegalStateException("boom")
        var result: InboxResult<Int>? = null

        KlaviyoInbox.updateMessageState(InboxStateChange.ARCHIVE, InboxFilter.ALL_MESSAGES) {
            result = it
        }

        assertIs<InboxException.StorageFailure>(assertIs<InboxResult.Failure>(result).exception)
    }

    @Test
    fun `bulk updateMessageState throws when the inbox module is absent`() {
        inbox.unregister()

        assertThrows(MissingKlaviyoModule::class.java) {
            KlaviyoInbox.updateMessageState(InboxStateChange.MARK_READ, InboxFilter.ALL_MESSAGES) { }
        }
    }

    @Test
    fun `a failed state update reaches the callback as a failure`() {
        val failure = InboxException.StorageFailure("store failed")
        inbox.failure = failure
        var result: InboxResult<Boolean>? = null

        KlaviyoInbox.updateMessageState("tm-1", InboxStateChange.MARK_READ) { result = it }

        assertSame(failure, assertIs<InboxResult.Failure>(result).exception)
    }

    @Test
    fun `the Java entry point for state updates throws when the inbox module is absent`() {
        inbox.unregister()

        assertThrows(MissingKlaviyoModule::class.java) {
            KlaviyoInbox.updateMessageState("tm-1", InboxStateChange.MARK_READ) { }
        }
    }

    @Test
    fun `clearAll removes the current company's messages and reports how many`() {
        inbox.clearedCount = 2
        var result: InboxResult<Int>? = null

        KlaviyoInbox.clearAll { result = it }

        assertEquals(2, assertIs<InboxResult.Success<Int>>(result).value)
        assertEquals(1, inbox.clearAllCalls)
    }

    @Test
    fun `the Kotlin entry point clears through the registered inbox`() = runBlocking {
        inbox.clearedCount = 3

        assertEquals(3, Klaviyo.mobileInbox.clearAll())
        assertEquals(1, inbox.clearAllCalls)
    }

    @Test
    fun `clearAll reports nothing removed`() {
        var result: InboxResult<Int>? = null

        KlaviyoInbox.clearAll { result = it }

        assertEquals(0, assertIs<InboxResult.Success<Int>>(result).value)
    }

    @Test
    fun `a failed clearAll reaches the callback as a failure`() {
        val failure = InboxException.StorageFailure("store failed")
        inbox.failure = failure
        var result: InboxResult<Int>? = null

        KlaviyoInbox.clearAll { result = it }

        assertSame(failure, assertIs<InboxResult.Failure>(result).exception)
    }

    @Test
    fun `the Java entry point for clearAll throws when the inbox module is absent`() {
        inbox.unregister()

        assertThrows(MissingKlaviyoModule::class.java) { KlaviyoInbox.clearAll { } }
    }

    @Test
    fun `an inbox failure reaches the callback as a failure`() {
        val failure = InboxException.StorageFailure("store failed")
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
        assertTrue(error is InboxException.StorageFailure)
        assertSame(failure, error.cause)
        verify { spyLog.error("Mobile Inbox call failed", failure) }
    }

    @Test
    fun `a change listener hears each change until it is removed`() {
        var heard = 0
        val listener = InboxChangeListener { heard++ }
        KlaviyoInbox.addChangeListener(listener)

        inbox.emitChange()
        assertEquals(1, heard)

        KlaviyoInbox.removeChangeListener(listener)
        inbox.emitChange()
        assertEquals(1, heard)
    }

    @Test
    fun `a listener added twice hears a change once`() {
        var heard = 0
        val listener = InboxChangeListener { heard++ }
        KlaviyoInbox.addChangeListener(listener)
        KlaviyoInbox.addChangeListener(listener)

        inbox.emitChange()

        assertEquals(1, heard)
        KlaviyoInbox.removeChangeListener(listener)
    }

    @Test
    fun `a change already waiting for the main thread is dropped after the listener is removed`() {
        val waiting = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers { waiting += firstArg<() -> Unit>() }
        var heard = 0
        val listener = InboxChangeListener { heard++ }
        KlaviyoInbox.addChangeListener(listener)
        inbox.emitChange()

        KlaviyoInbox.removeChangeListener(listener)
        waiting.forEach { it() }

        assertEquals(0, heard)
    }

    @Test
    fun `adding a listener again does not revive a change waiting from before it was removed`() {
        val waiting = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers { waiting += firstArg<() -> Unit>() }
        var heard = 0
        val listener = InboxChangeListener { heard++ }
        KlaviyoInbox.addChangeListener(listener)
        inbox.emitChange()
        KlaviyoInbox.removeChangeListener(listener)
        KlaviyoInbox.addChangeListener(listener)

        waiting.forEach { it() }
        assertEquals(0, heard)

        inbox.emitChange()
        waiting.drop(1).forEach { it() }
        assertEquals(1, heard)
        KlaviyoInbox.removeChangeListener(listener)
    }

    @Test
    fun `listeners hear changes independently`() {
        var first = 0
        var second = 0
        val a = InboxChangeListener { first++ }
        val b = InboxChangeListener { second++ }
        KlaviyoInbox.addChangeListener(a)
        KlaviyoInbox.addChangeListener(b)

        KlaviyoInbox.removeChangeListener(a)
        inbox.emitChange()

        assertEquals(0, first)
        assertEquals(1, second)
        KlaviyoInbox.removeChangeListener(b)
    }

    @Test
    fun `a listener whose collection failed can be added again`() {
        var heard = 0
        val listener = InboxChangeListener { heard++ }
        inbox.changesFailure = IllegalStateException("no repository")
        KlaviyoInbox.addChangeListener(listener)

        verify { spyLog.error("Mobile Inbox change listener stopped", any()) }

        inbox.changesFailure = null
        KlaviyoInbox.addChangeListener(listener)
        inbox.emitChange()

        assertEquals(1, heard)
        KlaviyoInbox.removeChangeListener(listener)
    }

    @Test
    fun `removing a listener that was never added does nothing`() {
        KlaviyoInbox.removeChangeListener { }
    }

    @Test
    fun `the Java entry point for change listeners throws when the inbox module is absent`() {
        inbox.unregister()

        assertThrows(MissingKlaviyoModule::class.java) { KlaviyoInbox.addChangeListener { } }
    }
}

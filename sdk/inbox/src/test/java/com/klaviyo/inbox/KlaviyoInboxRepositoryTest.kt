package com.klaviyo.inbox

import com.klaviyo.core.config.MissingAPIKey
import com.klaviyo.fixtures.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

internal class KlaviyoInboxRepositoryTest : BaseTest() {

    private val dataSource = mockk<LocalInboxDataSource>(relaxed = true)
    private val repository = KlaviyoInboxRepository(dataSource, retentionLimit = RETENTION_LIMIT)
    private val message = InboxMessageEntity(
        transmissionId = "tm-1",
        companyId = API_KEY,
        title = "Sale today",
        body = null,
        defaultDestination = InboxRecordDestination("open_app", null),
        mediaUrl = null,
        mediaType = null,
        customData = emptyMap(),
        actions = emptyList(),
        sentAt = null,
        collapseId = null,
        badge = null,
        receivedAt = TIME,
        attributionJson = null
    )

    @Test
    fun `a stored message is captured with the retention limit`() = runBlocking {
        coEvery { dataSource.insert(any(), any()) } returns InboxInsertResult.INSERTED

        assertEquals(InboxCaptureResult.CAPTURED, repository.capture(message))
        coVerify { dataSource.insert(message, RETENTION_LIMIT) }
    }

    @Test
    fun `a repeat message is reported as a duplicate`() = runBlocking {
        coEvery { dataSource.insert(any(), any()) } returns InboxInsertResult.DUPLICATE

        assertEquals(InboxCaptureResult.DUPLICATE, repository.capture(message))
    }

    @Test
    fun `a storage failure is logged and reported as failed`() = runBlocking {
        coEvery { dataSource.insert(any(), any()) } throws IllegalStateException("disk full")

        assertEquals(InboxCaptureResult.FAILED, repository.capture(message))
        verify { spyLog.error(any(), any<IllegalStateException>()) }
    }

    @Test
    fun `cancellation is not reported as a failed capture`() {
        coEvery { dataSource.insert(any(), any()) } throws CancellationException("cancelled")

        assertThrows(CancellationException::class.java) {
            runBlocking { repository.capture(message) }
        }
        verify(exactly = 0) { spyLog.error(any(), any<Throwable>()) }
    }

    @Test
    fun `clears are passed to the data source`() = runBlocking {
        repository.clearByCompanyId(API_KEY)
        repository.clearAll()

        coVerify { dataSource.clearByCompanyId(API_KEY) }
        coVerify { dataSource.clearAll() }
    }

    @Test
    fun `a page maps rows to public messages and a cursor when more follow`() = runBlocking {
        val second = message.copy(transmissionId = "tm-2", sortTime = TIME - 1)
        coEvery { dataSource.fetchPage(any(), any()) } returns
            InboxRecordPage(listOf(message, second), hasMore = true)

        val page = repository.fetchPage(InboxQuery(pageSize = 2))

        assertEquals(listOf("tm-1", "tm-2"), page.messages.map { it.id })
        assertEquals(InboxMessageSource.CAPTURED, page.messages.first().source)
        val next = page.nextCursor
        assertNotNull(next)
        assertEquals(
            InboxRecordPosition(second.sortTime, "tm-2"),
            InboxCursorCodec.decode(InboxQuery(pageSize = 2, cursor = next))
        )
    }

    @Test
    fun `the last page has no cursor`() = runBlocking {
        coEvery { dataSource.fetchPage(any(), any()) } returns
            InboxRecordPage(listOf(message), hasMore = false)

        assertNull(repository.fetchPage(InboxQuery()).nextCursor)
    }

    @Test
    fun `a page is read for the current company with the query's filters`() = runBlocking {
        coEvery { dataSource.fetchPage(any(), any()) } returns InboxRecordPage(emptyList(), false)

        repository.fetchPage(
            InboxQuery(
                pageSize = 5,
                readFilter = InboxReadFilter.UNREAD,
                archiveFilter = InboxArchiveFilter.ALL,
                sortOrder = InboxSortOrder.OLDEST_FIRST
            )
        )

        coVerify {
            dataSource.fetchPage(
                API_KEY,
                InboxRecordQuery(
                    5,
                    null,
                    InboxReadFilter.UNREAD,
                    InboxArchiveFilter.ALL,
                    InboxSortOrder.OLDEST_FIRST
                )
            )
        }
    }

    @Test
    fun `a cursor from another query is rejected without reading the store`() {
        val cursor = InboxCursorCodec.encode(InboxQuery(), message)
        val other = InboxQuery(cursor = cursor, readFilter = InboxReadFilter.UNREAD)

        val error = assertThrows(InboxException::class.java) {
            runBlocking { repository.fetchPage(other) }
        }

        assertEquals(InboxException.Reason.INVALID_CURSOR, error.reason)
        coVerify(exactly = 0) { dataSource.fetchPage(any(), any()) }
    }

    @Test
    fun `reads before the SDK is initialized are empty`() = runBlocking {
        every { mockConfig.apiKey } throws MissingAPIKey()

        assertTrue(repository.fetchPage(InboxQuery()).messages.isEmpty())
        assertEquals(0, repository.fetchUnreadCount())
        assertFalse(repository.updateMessageState("tm-1", InboxStateChange.MARK_READ))
        coVerify(exactly = 0) { dataSource.fetchPage(any(), any()) }
    }

    @Test
    fun `unread count and state changes use the current company`() = runBlocking {
        coEvery { dataSource.countUnread(any()) } returns 3
        coEvery { dataSource.updateState(any(), any(), any()) } returns true

        assertEquals(3, repository.fetchUnreadCount())
        assertTrue(repository.updateMessageState("tm-1", InboxStateChange.ARCHIVE))

        coVerify { dataSource.countUnread(API_KEY) }
        coVerify { dataSource.updateState(API_KEY, "tm-1", InboxRecordStateChange.ARCHIVE) }
    }

    @Test
    fun `a store failure on read surfaces as a storage failure`() {
        coEvery { dataSource.countUnread(any()) } throws IllegalStateException("corrupt")

        val error = assertThrows(InboxException::class.java) {
            runBlocking { repository.fetchUnreadCount() }
        }

        assertEquals(InboxException.Reason.STORAGE_FAILURE, error.reason)
        verify { spyLog.error(any(), any<IllegalStateException>()) }
    }

    @Test
    fun `changes come from the data source`() {
        val flow = emptyFlow<Unit>()
        every { dataSource.changes } returns flow

        assertSame(flow, repository.changes)
    }

    private companion object {
        const val RETENTION_LIMIT = 25
    }
}

package com.klaviyo.inbox

import com.klaviyo.core.config.MissingAPIKey
import com.klaviyo.fixtures.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun `a capture after a failed one still stores the message`() = runBlocking {
        coEvery { dataSource.insert(any(), any()) } throws IllegalStateException("disk full") andThen
            InboxInsertResult.INSERTED

        assertEquals(InboxCaptureResult.FAILED, repository.capture(message))
        assertEquals(InboxCaptureResult.CAPTURED, repository.capture(message))
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
    fun `a failed clear by company is logged and does not throw`() = runBlocking {
        coEvery { dataSource.clearByCompanyId(any()) } throws IllegalStateException("disk full")

        repository.clearByCompanyId(API_KEY)

        verify { spyLog.error(any(), any<IllegalStateException>()) }
    }

    @Test
    fun `a failed clear all is logged and does not throw`() = runBlocking {
        coEvery { dataSource.clearAll() } throws IllegalStateException("disk full")

        repository.clearAll()

        verify { spyLog.error(any(), any<IllegalStateException>()) }
    }

    @Test
    fun `cancellation is not swallowed by a clear`() {
        coEvery { dataSource.clearAll() } throws CancellationException("cancelled")
        coEvery { dataSource.clearByCompanyId(any()) } throws CancellationException("cancelled")

        assertThrows(CancellationException::class.java) { runBlocking { repository.clearAll() } }
        assertThrows(CancellationException::class.java) {
            runBlocking { repository.clearByCompanyId(API_KEY) }
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
    fun `unread count is read for the current company`() = runBlocking {
        coEvery { dataSource.countUnread(any()) } returns 3

        assertEquals(3, repository.fetchUnreadCount())
        coVerify { dataSource.countUnread(API_KEY) }
    }

    @Test
    fun `unread count is zero before the SDK is initialized`() = runBlocking {
        every { mockConfig.apiKey } throws MissingAPIKey()

        assertEquals(0, repository.fetchUnreadCount())
        coVerify(exactly = 0) { dataSource.countUnread(any()) }
        verify(exactly = 0) { spyLog.error(any(), any<Throwable>()) }
    }

    @Test
    fun `a store failure on read surfaces as a storage failure`() {
        coEvery { dataSource.countUnread(any()) } throws IllegalStateException("corrupt")

        val error = assertThrows(InboxException::class.java) {
            runBlocking { repository.fetchUnreadCount() }
        }

        assertEquals(InboxException.Reason.STORAGE_FAILURE, error.reason)
        assertTrue(error.cause is IllegalStateException)
        verify { spyLog.error(any(), any<IllegalStateException>()) }
    }

    @Test
    fun `cancellation is not reported as a storage failure`() {
        coEvery { dataSource.countUnread(any()) } throws CancellationException("cancelled")

        assertThrows(CancellationException::class.java) {
            runBlocking { repository.fetchUnreadCount() }
        }
    }

    private companion object {
        const val RETENTION_LIMIT = 25
    }
}

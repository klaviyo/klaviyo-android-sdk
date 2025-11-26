package com.klaviyo.analytics.networking

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class QueueFlushWorkerTest : BaseTest() {

    private val workerParams = mockk<WorkerParameters>(relaxed = true)

    @Before
    override fun setup() {
        super.setup()

        // Mock KlaviyoApiClient since getOrNull is an inline reified function
        // and cannot be easily mocked. The worker falls back to KlaviyoApiClient
        // when getOrNull returns null.
        mockkObject(KlaviyoApiClient)
        every { KlaviyoApiClient.flushQueue() } returns Unit
        every { KlaviyoApiClient.startService() } returns Unit
    }

    @After
    override fun cleanup() {
        unmockkObject(KlaviyoApiClient)
        super.cleanup()
    }

    @Test
    fun `Worker flushes queue successfully`() = runTest {
        // Build the worker
        val worker = QueueFlushWorker(mockContext, workerParams)

        // Execute the work
        val result = worker.doWork()

        // Verify success
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify queue was flushed
        verify(exactly = 1) { KlaviyoApiClient.flushQueue() }
    }

    @Test
    fun `Worker returns success even when exception occurs`() = runTest {
        // Setup ApiClient to throw an exception
        every { KlaviyoApiClient.flushQueue() } throws RuntimeException("Test exception")

        // Build the worker
        val worker = QueueFlushWorker(mockContext, workerParams)

        // Execute the work
        val result = worker.doWork()

        // Should still return success to prevent WorkManager retries
        assertEquals(ListenableWorker.Result.success(), result)
    }
}

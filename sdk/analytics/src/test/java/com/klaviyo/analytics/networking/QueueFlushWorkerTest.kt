package com.klaviyo.analytics.networking

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.fixtures.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class QueueFlushWorkerTest : BaseTest() {

    private val mockApiClient = mockk<ApiClient>(relaxed = true).apply {
        every { restoreQueue() } returns Unit
        coEvery { awaitFlushQueueOutcome() } returns FlushOutcome.Complete
    }
    private val workerParams = mockk<WorkerParameters>(relaxed = true)

    @Before
    override fun setup() {
        super.setup()

        // Mock Klaviyo to prevent initialization attempts
        // mockkStatic is required for @JvmStatic methods
        mockkStatic(Klaviyo::class)
        mockkObject(Klaviyo)
        every { Klaviyo.registerForLifecycleCallbacks(any()) } answers {
            Registry.register<ApiClient>(mockApiClient)
            Klaviyo
        }
    }

    @After
    override fun cleanup() {
        unmockkObject(Klaviyo)
        unmockkStatic(Klaviyo::class)
        Registry.unregister<ApiClient>()
        super.cleanup()
    }

    // Build worker and run the job
    private suspend fun doWork() = QueueFlushWorker(mockContext, workerParams).doWork()

    @Test
    fun `Worker flushes queue successfully from uninitialized state`() = runTest {
        val result = doWork()

        // Verify success
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify queue was flushed
        verify(exactly = 1) { Klaviyo.registerForLifecycleCallbacks(mockContext) }
        verify(exactly = 1) { mockApiClient.restoreQueue() }
        coVerify(exactly = 1) { mockApiClient.awaitFlushQueueOutcome() }
    }

    @Test
    fun `Worker flushes queue successfully from pre-initialized state`() = runTest {
        // Pre-register dependencies
        Registry.register<Config>(mockConfig)
        Registry.register<ApiClient>(mockApiClient)

        // Execute the work
        val result = doWork()

        // Verify success
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify queue was flushed, and didn't need to configure dependencies
        verify(inverse = true) { Klaviyo.registerForLifecycleCallbacks(mockContext) }
        verify(exactly = 1) { mockApiClient.restoreQueue() }
        coVerify(exactly = 1) { mockApiClient.awaitFlushQueueOutcome() }
    }

    @Test
    fun `Worker returns success even when exception occurs`() = runTest {
        // Setup ApiClient to throw an exception
        every { mockApiClient.restoreQueue() } throws RuntimeException("Test exception")

        val result = doWork()

        // Should still return success to prevent WorkManager retries, but flush wasn't called
        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 1) { mockApiClient.restoreQueue() }
        coVerify(inverse = true) { mockApiClient.awaitFlushQueueOutcome() }
    }
}

package com.klaviyo.analytics.networking

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
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

    private val mockApiClient = mockk<ApiClient>(relaxed = true).apply {
        every { startService() } returns Unit
        every { flushQueue() } returns Unit
    }
    private val workerParams = mockk<WorkerParameters>(relaxed = true)

    @Before
    override fun setup() {
        super.setup()

        // Mock Klaviyo to prevent initialization attempts
        mockkObject(Klaviyo)
        every { Klaviyo.registerForLifecycleCallbacks(any()) } answers {
            Registry.register<ApiClient>(mockApiClient)
            Klaviyo
        }
    }

    @After
    override fun cleanup() {
        unmockkObject(Klaviyo)
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
        verify(exactly = 1) { mockApiClient.startService() }
        verify(exactly = 1) { mockApiClient.flushQueue() }
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
        verify(exactly = 1) { mockApiClient.startService() }
        verify(exactly = 1) { mockApiClient.flushQueue() }
    }

    @Test
    fun `Worker returns success even when exception occurs`() = runTest {
        // Setup ApiClient to throw an exception
        every { mockApiClient.startService() } throws RuntimeException("Test exception")

        val result = doWork()

        // Should still return success to prevent WorkManager retries, but flush wasn't called
        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 1) { mockApiClient.startService() }
        verify(inverse = true) { mockApiClient.flushQueue() }
    }
}

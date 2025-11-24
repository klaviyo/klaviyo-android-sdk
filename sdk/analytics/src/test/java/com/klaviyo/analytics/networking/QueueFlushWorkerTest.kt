package com.klaviyo.analytics.networking

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.klaviyo.core.Registry
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

    private lateinit var context: Context
    private lateinit var mockApiClient: ApiClient

    @Before
    override fun setup() {
        super.setup()
        context = mockk(relaxed = true)
        mockApiClient = mockk(relaxed = true)

        // Mock the Registry to return our mock ApiClient
        every { Registry.getOrNull<ApiClient>() } returns mockApiClient
    }

    @After
    override fun cleanup() {
        super.cleanup()
    }

    @Test
    fun `Worker flushes queue when ApiClient is already registered`() = runTest {
        // Build the worker
        val worker = TestListenableWorkerBuilder<QueueFlushWorker>(context)
            .build()

        // Execute the work
        val result = worker.startWork().get()

        // Verify success
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify queue was flushed
        verify(exactly = 1) { mockApiClient.flushQueue() }
        verify { Registry.log.info("WorkManager triggered queue flush") }
    }

    @Test
    fun `Worker initializes ApiClient when not registered`() = runTest {
        // Setup Registry to return null initially (ApiClient not registered)
        every { Registry.getOrNull<ApiClient>() } returns null
        every { Registry.isRegistered<ApiClient>() } returns false

        // Mock the KlaviyoApiClient object
        mockkObject(KlaviyoApiClient)
        every { KlaviyoApiClient.startService() } returns Unit
        every { KlaviyoApiClient.flushQueue() } returns Unit
        every { Registry.register<ApiClient>(KlaviyoApiClient) } returns Unit

        // Build the worker
        val worker = TestListenableWorkerBuilder<QueueFlushWorker>(context)
            .build()

        // Execute the work
        val result = worker.startWork().get()

        // Verify success
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify ApiClient was initialized and registered
        verify(exactly = 1) { Registry.isRegistered<ApiClient>() }
        verify(exactly = 1) { Registry.register<ApiClient>(KlaviyoApiClient) }
        verify(exactly = 1) { KlaviyoApiClient.startService() }
        verify(exactly = 1) { KlaviyoApiClient.flushQueue() }
        verify { Registry.log.info("WorkManager triggered queue flush") }

        unmockkObject(KlaviyoApiClient)
    }

    @Test
    fun `Worker returns success even when exception occurs`() = runTest {
        // Setup ApiClient to throw an exception
        every { mockApiClient.flushQueue() } throws RuntimeException("Test exception")

        // Build the worker
        val worker = TestListenableWorkerBuilder<QueueFlushWorker>(context)
            .build()

        // Execute the work
        val result = worker.startWork().get()

        // Should still return success to prevent WorkManager retries
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify error was logged
        verify { Registry.log.error("WorkManager queue flush failed", any()) }
    }

    @Test
    fun `Worker handles ApiClient already registered after process death`() = runTest {
        // Simulate process death scenario where ApiClient exists but not in Registry
        every { Registry.getOrNull<ApiClient>() } returns null
        every { Registry.isRegistered<ApiClient>() } returns true // Already registered

        // Since it's already registered, we shouldn't register again
        // The worker should handle this gracefully

        mockkObject(KlaviyoApiClient)
        every { KlaviyoApiClient.flushQueue() } returns Unit

        // Build the worker
        val worker = TestListenableWorkerBuilder<QueueFlushWorker>(context)
            .build()

        // Execute the work
        val result = worker.startWork().get()

        // Verify success
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify that we didn't try to register again since it was already registered
        verify(exactly = 0) { Registry.register<ApiClient>(any()) }
        verify(exactly = 1) { KlaviyoApiClient.flushQueue() }

        unmockkObject(KlaviyoApiClient)
    }
}

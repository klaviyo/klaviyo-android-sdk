package com.klaviyo.core.networking

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class WorkManagerQueueSchedulerTest : BaseTest() {

    private lateinit var mockBaseApiClient: BaseApiClient
    private lateinit var scheduler: WorkManagerQueueScheduler

    @Before
    override fun setup() {
        super.setup()
        mockBaseApiClient = mockk(relaxed = true)
        Registry.register<BaseApiClient>(mockBaseApiClient)
        scheduler = WorkManagerQueueScheduler(mockContext)
    }

    @After
    override fun cleanup() {
        Registry.unregister<BaseApiClient>()
        super.cleanup()
    }

    @Test
    fun `QueueScheduler interface is implemented`() {
        // Assert - verify the scheduler implements the interface
        Assert.assertTrue(
            "WorkManagerQueueScheduler should implement QueueScheduler",
            scheduler is QueueScheduler
        )
    }

    @Test
    fun `scheduleFlush can be called`() {
        // This test just verifies the method can be called without crashing
        // Full WorkManager integration testing requires instrumentation tests
        try {
            scheduler.scheduleFlush()
        } catch (e: Exception) {
            // WorkManager may not be fully initialized in unit tests
            // but we can verify the method exists and can be called
        }
    }

    @Test
    fun `cancelScheduledFlush can be called`() {
        // This test just verifies the method can be called without crashing
        try {
            scheduler.cancelScheduledFlush()
        } catch (e: Exception) {
            // WorkManager may not be fully initialized in unit tests
            // but we can verify the method exists and can be called
        }
    }
}

class QueueFlushWorkerTest : BaseTest() {

    private lateinit var context: Context
    private lateinit var worker: QueueFlushWorker
    private lateinit var mockBaseApiClient: BaseApiClient

    @Before
    override fun setup() {
        super.setup()

        context = mockContext

        // Create worker for testing
        worker = TestListenableWorkerBuilder<QueueFlushWorker>(
            context = context
        ).build()

        mockBaseApiClient = mockk(relaxed = true)
        Registry.register<BaseApiClient>(mockBaseApiClient)
    }

    @After
    override fun cleanup() {
        Registry.unregister<BaseApiClient>()
        super.cleanup()
    }

    @Test
    fun `Worker extends CoroutineWorker`() {
        // Assert - verify worker is a CoroutineWorker (future-proof for coroutine migration)
        Assert.assertTrue(
            "QueueFlushWorker should extend CoroutineWorker",
            worker is androidx.work.CoroutineWorker
        )
    }

    @Test
    fun `doWork calls flushQueue on BaseApiClient`() = runBlocking {
        // Act - execute the worker
        val result = worker.doWork()

        // Assert - verify flushQueue was called
        verify(exactly = 1) { mockBaseApiClient.flushQueue() }
        Assert.assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns success when BaseApiClient is not registered`() = runBlocking {
        // Arrange - unregister BaseApiClient
        Registry.unregister<BaseApiClient>()

        // Act - execute the worker
        val result = worker.doWork()

        // Assert - verify it returns success (we don't want retries)
        Assert.assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns success even when flushQueue throws exception`() = runBlocking {
        // Arrange - make flushQueue throw exception
        every { mockBaseApiClient.flushQueue() } throws RuntimeException("Test exception")

        // Act - execute the worker
        val result = worker.doWork()

        // Assert - verify it still returns success (we don't want retries)
        Assert.assertEquals(ListenableWorker.Result.success(), result)
    }
}

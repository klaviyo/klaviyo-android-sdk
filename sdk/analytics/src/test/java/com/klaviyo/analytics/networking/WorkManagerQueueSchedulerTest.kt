package com.klaviyo.analytics.networking

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class WorkManagerQueueSchedulerTest : BaseTest() {

    private lateinit var mockAppContext: Context
    private lateinit var mockWorkManager: WorkManager
    private lateinit var scheduler: WorkManagerQueueScheduler
    private val workRequestSlot = slot<OneTimeWorkRequest>()
    private val workNameSlot = slot<String>()
    private val policySlot = slot<ExistingWorkPolicy>()

    @Before
    override fun setup() {
        super.setup()

        mockAppContext = mockk(relaxed = true)
        mockWorkManager = mockk(relaxed = true)

        mockkStatic(WorkManager::class)
        // Use any() matcher for context since WorkManager.getInstance may be called with different contexts
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        scheduler = WorkManagerQueueScheduler(mockAppContext)
    }

    @After
    override fun cleanup() {
        unmockkStatic(WorkManager::class)
        super.cleanup()
    }

    @Test
    fun `Schedule flush enqueues work with correct configuration`() {
        // Capture the work request
        every {
            mockWorkManager.enqueueUniqueWork(
                capture(workNameSlot),
                capture(policySlot),
                capture(workRequestSlot)
            )
        } returns mockk()

        // Act
        scheduler.scheduleFlush()

        // Assert
        verify(exactly = 1) {
            mockWorkManager.enqueueUniqueWork(
                any(),
                any(),
                any<OneTimeWorkRequest>()
            )
        }

        // Verify work name
        assertEquals("klaviyo_queue_flush", workNameSlot.captured)

        // Verify policy is KEEP to prevent duplicates
        assertEquals(ExistingWorkPolicy.KEEP, policySlot.captured)

        // Verify work request configuration
        val workRequest = workRequestSlot.captured
        assertTrue(workRequest.workSpec.workerClassName.endsWith("QueueFlushWorker"))

        // Verify constraints - network connectivity required
        val constraints = workRequest.workSpec.constraints
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)

        // Verify expedited work with fallback
        assertEquals(
            OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST,
            workRequest.workSpec.outOfQuotaPolicy
        )
    }

    @Test
    fun `Cancel scheduled flush cancels work by name`() {
        // Act
        scheduler.cancelScheduledFlush()

        // Assert
        verify(exactly = 1) { mockWorkManager.cancelUniqueWork("klaviyo_queue_flush") }
    }

    @Test
    fun `Multiple schedule calls use KEEP policy to prevent duplicates`() {
        // Capture all calls
        every {
            mockWorkManager.enqueueUniqueWork(
                capture(workNameSlot),
                capture(policySlot),
                any<OneTimeWorkRequest>()
            )
        } returns mockk()

        // Act - schedule multiple times
        scheduler.scheduleFlush()
        scheduler.scheduleFlush()
        scheduler.scheduleFlush()

        // Assert - all calls should use KEEP policy
        verify(exactly = 3) {
            mockWorkManager.enqueueUniqueWork(
                any(),
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }
    }
}

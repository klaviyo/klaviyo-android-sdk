package com.klaviyo.core.lifecycle

import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.mockk
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class KlaviyoLifecycleMonitorTest : BaseTest() {

    @Test
    fun `Is registered service`() {
        unmockkObject(Registry)
        assertEquals(KlaviyoLifecycleMonitor, Registry.lifecycleMonitor)
    }

    @Test
    fun `Callbacks are invoked when all activities are stopped`() {
        var callCount = 0

        KlaviyoLifecycleMonitor.onActivityEvent {
            if (it is ActivityEvent.AllStopped) callCount++
        }

        KlaviyoLifecycleMonitor.onActivityStarted(mockk())
        KlaviyoLifecycleMonitor.onActivityStarted(mockk())
        KlaviyoLifecycleMonitor.onActivityStopped(mockk())
        assert(callCount == 0)
        KlaviyoLifecycleMonitor.onActivityStopped(mockk())
        assert(callCount == 1)

        // At this time, we expect nothing from this methods:
        KlaviyoLifecycleMonitor.onActivityCreated(mockk(), mockk())
        KlaviyoLifecycleMonitor.onActivityResumed(mockk())
        KlaviyoLifecycleMonitor.onActivitySaveInstanceState(mockk(), mockk())
        KlaviyoLifecycleMonitor.onActivityPaused(mockk())
        KlaviyoLifecycleMonitor.onActivityDestroyed(mockk())
        assert(callCount == 1)
    }

    @Test
    fun `Lifecycle events are logged`() {
        // At this time, we expect nothing from this methods:
        KlaviyoLifecycleMonitor.onActivityStarted(mockk())
        verify { logSpy.info("Started") }
        KlaviyoLifecycleMonitor.onActivityCreated(mockk(), mockk())
        verify { logSpy.info("Created") }
        KlaviyoLifecycleMonitor.onActivityResumed(mockk())
        verify { logSpy.info("Resumed") }
        KlaviyoLifecycleMonitor.onActivitySaveInstanceState(mockk(), mockk())
        verify { logSpy.info("SaveInstanceState") }
        KlaviyoLifecycleMonitor.onActivityPaused(mockk())
        verify { logSpy.info("Paused") }
        KlaviyoLifecycleMonitor.onActivityDestroyed(mockk())
        verify { logSpy.info("Destroyed") }
        KlaviyoLifecycleMonitor.onActivityStopped(mockk())
        verify { logSpy.info("Stopped") }
        verify { logSpy.info("AllStopped") }
    }

    @Test
    fun `All events are invoked`() {
        var createdCount = 0
        var startedCount = 0
        var resumedCount = 0
        var saveInstanceStateCount = 0
        var pausedCount = 0
        var stoppedCount = 0
        var allStoppedCount = 0
        var destroyedCount = 0

        KlaviyoLifecycleMonitor.onActivityEvent {
            when (it) {
                is ActivityEvent.Created -> createdCount++
                is ActivityEvent.Started -> startedCount++
                is ActivityEvent.Resumed -> resumedCount++
                is ActivityEvent.SaveInstanceState -> saveInstanceStateCount++
                is ActivityEvent.Paused -> pausedCount++
                is ActivityEvent.Stopped -> stoppedCount++
                is ActivityEvent.AllStopped -> allStoppedCount++
                is ActivityEvent.Destroyed -> destroyedCount++
            }
        }

        KlaviyoLifecycleMonitor.onActivityCreated(mockk(), mockk())
        KlaviyoLifecycleMonitor.onActivityStarted(mockk())
        KlaviyoLifecycleMonitor.onActivityResumed(mockk())
        KlaviyoLifecycleMonitor.onActivitySaveInstanceState(mockk(), mockk())
        KlaviyoLifecycleMonitor.onActivityPaused(mockk())
        KlaviyoLifecycleMonitor.onActivityStopped(mockk())
        KlaviyoLifecycleMonitor.onActivityDestroyed(mockk())

        assertEquals(1, createdCount)
        assertEquals(1, startedCount)
        assertEquals(1, resumedCount)
        assertEquals(1, saveInstanceStateCount)
        assertEquals(1, pausedCount)
        assertEquals(1, stoppedCount)
        assertEquals(1, allStoppedCount)
        assertEquals(1, destroyedCount)
    }

    @Test
    fun `Observer can be removed`() {
        var callCount = 0
        val observer: ActivityObserver = { callCount++ }

        KlaviyoLifecycleMonitor.onActivityEvent(observer)
        KlaviyoLifecycleMonitor.onActivityStarted(mockk())
        assert(callCount == 1)

        KlaviyoLifecycleMonitor.offActivityEvent(observer)
        KlaviyoLifecycleMonitor.onActivityStopped(mockk())
        assert(callCount == 1)
    }
}

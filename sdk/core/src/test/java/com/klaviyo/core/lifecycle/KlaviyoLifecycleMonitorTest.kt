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
        assertEquals(0, callCount)
        KlaviyoLifecycleMonitor.onActivityStopped(mockk())
        assertEquals(1, callCount)

        // At this time, we expect nothing from this methods:
        KlaviyoLifecycleMonitor.onActivityCreated(mockk(), mockk())
        KlaviyoLifecycleMonitor.onActivityResumed(mockk())
        KlaviyoLifecycleMonitor.onActivitySaveInstanceState(mockk(), mockk())
        KlaviyoLifecycleMonitor.onActivityPaused(mockk())
        KlaviyoLifecycleMonitor.onActivityDestroyed(mockk())
        assertEquals(1, callCount)
    }

    @Test
    fun `Lifecycle events are logged`() {
        // At this time, we expect nothing from this methods:
        KlaviyoLifecycleMonitor.onActivityStarted(mockk())
        verify { spyLog.verbose("Started") }
        KlaviyoLifecycleMonitor.onActivityCreated(mockk(), mockk())
        verify { spyLog.verbose("Created") }
        KlaviyoLifecycleMonitor.onActivityResumed(mockk())
        verify { spyLog.verbose("Resumed") }
        KlaviyoLifecycleMonitor.onActivitySaveInstanceState(mockk(), mockk())
        verify { spyLog.verbose("SaveInstanceState") }
        KlaviyoLifecycleMonitor.onActivityPaused(mockk())
        verify { spyLog.verbose("Paused") }
        KlaviyoLifecycleMonitor.onActivityStopped(mockk())
        verify { spyLog.verbose("Stopped") }
        verify { spyLog.verbose("AllStopped") }
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

        KlaviyoLifecycleMonitor.onActivityEvent {
            when (it) {
                is ActivityEvent.Created -> createdCount++
                is ActivityEvent.Started -> startedCount++
                is ActivityEvent.Resumed -> resumedCount++
                is ActivityEvent.SaveInstanceState -> saveInstanceStateCount++
                is ActivityEvent.Paused -> pausedCount++
                is ActivityEvent.Stopped -> stoppedCount++
                is ActivityEvent.AllStopped -> allStoppedCount++
            }
        }

        KlaviyoLifecycleMonitor.onActivityCreated(mockk(), mockk())
        KlaviyoLifecycleMonitor.onActivityStarted(mockk())
        KlaviyoLifecycleMonitor.onActivityResumed(mockk())
        KlaviyoLifecycleMonitor.onActivitySaveInstanceState(mockk(), mockk())
        KlaviyoLifecycleMonitor.onActivityPaused(mockk())
        KlaviyoLifecycleMonitor.onActivityStopped(mockk())

        assertEquals(1, createdCount)
        assertEquals(1, startedCount)
        assertEquals(1, resumedCount)
        assertEquals(1, saveInstanceStateCount)
        assertEquals(1, pausedCount)
        assertEquals(1, stoppedCount)
        assertEquals(1, allStoppedCount)
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

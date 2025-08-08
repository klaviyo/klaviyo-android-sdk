package com.klaviyo.core.lifecycle

import android.app.Activity
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.AdvancedAPI
import com.klaviyo.core.utils.takeIf
import com.klaviyo.fixtures.BaseTest
import io.mockk.mockk
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class KlaviyoLifecycleMonitorTest : BaseTest() {

    @After
    override fun cleanup() {
        super.cleanup()

        // This should reset the current activity to null by calling it stopped
        KlaviyoLifecycleMonitor.currentActivity?.let {
            KlaviyoLifecycleMonitor.onActivityStopped(it)
        }
    }

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
        var foregroundedCount = 0
        var startedCount = 0
        var resumedCount = 0
        var saveInstanceStateCount = 0
        var pausedCount = 0
        var stoppedCount = 0
        var allStoppedCount = 0
        var configChangeCount = 0

        KlaviyoLifecycleMonitor.onActivityEvent {
            when (it) {
                is ActivityEvent.Created -> createdCount++
                is ActivityEvent.FirstStarted -> foregroundedCount++
                is ActivityEvent.Started -> startedCount++
                is ActivityEvent.Resumed -> resumedCount++
                is ActivityEvent.SaveInstanceState -> saveInstanceStateCount++
                is ActivityEvent.Paused -> pausedCount++
                is ActivityEvent.Stopped -> stoppedCount++
                is ActivityEvent.AllStopped -> allStoppedCount++
                is ActivityEvent.ConfigurationChanged -> configChangeCount++
            }
        }

        KlaviyoLifecycleMonitor.onActivityCreated(mockk(), mockk())
        KlaviyoLifecycleMonitor.onActivityStarted(mockk())
        KlaviyoLifecycleMonitor.onActivityResumed(mockk())
        KlaviyoLifecycleMonitor.onActivitySaveInstanceState(mockk(), mockk())
        KlaviyoLifecycleMonitor.onActivityPaused(mockk())
        KlaviyoLifecycleMonitor.onActivityStopped(mockk())
        KlaviyoLifecycleMonitor.onConfigurationChanged(mockk())

        assertEquals(1, createdCount)
        assertEquals(1, foregroundedCount)
        assertEquals(1, startedCount)
        assertEquals(1, resumedCount)
        assertEquals(1, saveInstanceStateCount)
        assertEquals(1, pausedCount)
        assertEquals(1, stoppedCount)
        assertEquals(1, allStoppedCount)
        assertEquals(1, configChangeCount)
    }

    @Test
    fun `Observer can be removed`() {
        var callCount = 0
        val observer: ActivityObserver = { callCount++ }

        KlaviyoLifecycleMonitor.onActivityEvent(observer)
        KlaviyoLifecycleMonitor.onActivityResumed(mockk())
        assertEquals(1, callCount)

        KlaviyoLifecycleMonitor.offActivityEvent(observer)
        KlaviyoLifecycleMonitor.onActivityStopped(mockk())
        assertEquals(1, callCount)
    }

    @OptIn(AdvancedAPI::class)
    @Test
    fun `assignCurrentActivity allows overriding current activity`() {
        assertEquals(null, KlaviyoLifecycleMonitor.currentActivity)
        val mockActivity: Activity = mockk()
        KlaviyoLifecycleMonitor.assignCurrentActivity(mockActivity)
        assertEquals(mockActivity, KlaviyoLifecycleMonitor.currentActivity)
    }

    @OptIn(AdvancedAPI::class)
    @Test
    fun `assignCurrentActivity does not double count an activity that was already tracked`() {
        val mockActivity: Activity = mockk()
        var allStoppedCount = 0
        KlaviyoLifecycleMonitor.onActivityEvent {
            it.takeIf<ActivityEvent.AllStopped>()?.let() { allStoppedCount++ }
        }

        // Simulate a regular activity lifecycle tracking this activity
        KlaviyoLifecycleMonitor.onActivityStarted(mockActivity)
        KlaviyoLifecycleMonitor.onActivityResumed(mockActivity)

        // Then use assign to manually track it also
        KlaviyoLifecycleMonitor.assignCurrentActivity(mockActivity)

        // And simulate it stopping
        KlaviyoLifecycleMonitor.onActivityPaused(mockActivity)
        KlaviyoLifecycleMonitor.onActivityStopped(mockActivity)

        // It should still be cleared and the backgrounded event should have fired
        assertEquals(null, KlaviyoLifecycleMonitor.currentActivity)
        assertEquals(1, allStoppedCount)
    }

    @OptIn(AdvancedAPI::class)
    @Test
    fun `runWithCurrentOrNextActivity runs with currentActivity`() {
        val mockActivity: Activity = mockk()
        KlaviyoLifecycleMonitor.assignCurrentActivity(mockActivity)
        var called = false

        KlaviyoLifecycleMonitor.runWithCurrentOrNextActivity(
            timeout = 100
        ) { _ ->
            called = true
        }

        assert(called) { "Callback should be called immediately" }
    }

    @Test
    fun `runWithCurrentOrNextActivity waits for next activity if currentActivity is null`() {
        var called = false

        KlaviyoLifecycleMonitor.runWithCurrentOrNextActivity(
            timeout = 100
        ) { _ ->
            called = true
        }

        assert(!called) { "Callback should not be called yet" }
        staticClock.execute(50)
        KlaviyoLifecycleMonitor.onActivityResumed(mockk())
        assert(called) { "Callback should be called after activity resumed" }
    }

    @Test
    fun `runWithCurrentOrNextActivity fails if activity is not resumed within timeout`() {
        var called = false

        KlaviyoLifecycleMonitor.runWithCurrentOrNextActivity(
            timeout = 100
        ) { _ ->
            called = true
        }

        assert(!called) { "Callback should not be called yet" }
        staticClock.execute(150)
        KlaviyoLifecycleMonitor.onActivityResumed(mockk())
        assert(!called) { "Callback should not be called if timed out" }
    }
}

package com.klaviyo.core.lifecycle

import android.app.Activity
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.AdvancedAPI
import com.klaviyo.core.utils.takeIf
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
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

    @Test
    fun `runWithCurrentOrNextActivity invokes onTimeout if activity is not resumed in time`() {
        var called = false
        var timedOut = false

        KlaviyoLifecycleMonitor.runWithCurrentOrNextActivity(
            timeout = 100,
            onTimeout = { timedOut = true }
        ) { _ ->
            called = true
        }

        assert(!timedOut) { "Fallback should not be called yet" }
        staticClock.execute(150)
        assert(timedOut) { "Fallback should be called once timed out" }
        assert(!called) { "Callback should not be called if timed out" }
    }

    @Test
    fun `runWithCurrentOrNextActivity does not invoke onTimeout once the job has run`() {
        var called = false
        var timedOut = false

        KlaviyoLifecycleMonitor.runWithCurrentOrNextActivity(
            timeout = 100,
            onTimeout = { timedOut = true }
        ) { _ ->
            called = true
        }

        KlaviyoLifecycleMonitor.onActivityResumed(mockk())
        staticClock.execute(150)

        assert(called) { "Callback should be called after activity resumed" }
        assert(!timedOut) { "Fallback should not be called after the job already ran" }
    }

    @Test
    fun `runWithCurrentOrNextActivity runs the job at most once`() {
        var callCount = 0

        KlaviyoLifecycleMonitor.runWithCurrentOrNextActivity(
            timeout = 100
        ) { _ ->
            callCount++
        }

        KlaviyoLifecycleMonitor.onActivityResumed(mockk())
        KlaviyoLifecycleMonitor.onActivityResumed(mockk())

        assertEquals(1, callCount)
    }

    @Test
    fun `runWithCurrentOrNextActivity returns null when the job ran against the current activity`() {
        @OptIn(AdvancedAPI::class)
        KlaviyoLifecycleMonitor.assignCurrentActivity(mockk())

        val token = KlaviyoLifecycleMonitor.runWithCurrentOrNextActivity(timeout = 100) { }

        assertEquals(null, token)
    }

    @Test
    fun `cancelling the returned token abandons the job without running either branch`() {
        var called = false
        var timedOut = false

        val token = KlaviyoLifecycleMonitor.runWithCurrentOrNextActivity(
            timeout = 100,
            onTimeout = { timedOut = true }
        ) { _ ->
            called = true
        }

        assert(token?.cancel() == true) { "Cancelling a pending job should report success" }

        KlaviyoLifecycleMonitor.onActivityResumed(mockk())
        staticClock.execute(150)

        assert(!called) { "Callback should not run after cancellation" }
        assert(!timedOut) { "Fallback should not run after cancellation — this is not a deadline" }
    }

    @Test
    fun `runNow on the returned token abandons the job rather than invoking the fallback`() {
        var called = false
        var timedOut = false

        // KlaviyoPresentationManager cancels a postponed present via runNow, so it must not be a
        // back door into the timeout fallback.
        val token = KlaviyoLifecycleMonitor.runWithCurrentOrNextActivity(
            timeout = 100,
            onTimeout = { timedOut = true }
        ) { _ ->
            called = true
        }

        token?.runNow()

        KlaviyoLifecycleMonitor.onActivityResumed(mockk())
        staticClock.execute(150)

        assert(!called) { "Callback should not run after runNow" }
        assert(!timedOut) { "Fallback should not run after runNow" }
    }

    @Test
    fun `runWithCurrentOrNextActivity picks up an activity that resumed while registering`() {
        var called = false
        var timedOut = false
        val resumedActivity = mockk<Activity>()

        // An activity resumes and broadcasts between the initial currentActivity check and the
        // observer's registration, so the observer never sees the event. Driven through the two
        // reads the implementation makes — null first, then present.
        mockkObject(KlaviyoLifecycleMonitor)
        // This registers a real observer on the monitor singleton. Capture it so the test removes
        // it regardless of whether the code under test does; a leaked observer would otherwise fail
        // unrelated tests in this class rather than this one.
        var registeredObserver: ActivityObserver? = null
        try {
            every { KlaviyoLifecycleMonitor.currentActivity } returnsMany listOf(
                null,
                resumedActivity
            )
            every { KlaviyoLifecycleMonitor.onActivityEvent(any()) } answers {
                registeredObserver = firstArg()
                callOriginal()
            }

            val token = KlaviyoLifecycleMonitor.runWithCurrentOrNextActivity(
                timeout = 100,
                onTimeout = { timedOut = true }
            ) { activity ->
                called = true
                assertEquals(resumedActivity, activity)
            }

            assert(called) { "Job should run against the activity that resumed during registration" }
            assertEquals(null, token)

            staticClock.execute(150)
            assert(!timedOut) { "Fallback should not run after the job already ran" }

            // A settled wait must leave no observer behind, or a later resume runs the job again.
            registeredObserver?.let {
                verify { KlaviyoLifecycleMonitor.offActivityEvent(it) }
            }
        } finally {
            unmockkObject(KlaviyoLifecycleMonitor)
            // Defensive: if the assertion above ever fails, the leaked observer would otherwise
            // fail unrelated tests in this class rather than this one.
            registeredObserver?.let { KlaviyoLifecycleMonitor.offActivityEvent(it) }
        }
    }

    @Test
    fun `returns no token when the wait settles while registering`() {
        var called = false
        var timedOut = false

        mockkObject(KlaviyoLifecycleMonitor)
        var registeredObserver: ActivityObserver? = null
        try {
            every { KlaviyoLifecycleMonitor.currentActivity } returns null
            // Settle the wait during registration, the way the clock's own thread can.
            every { KlaviyoLifecycleMonitor.onActivityEvent(any()) } answers {
                registeredObserver = firstArg()
                callOriginal()
                staticClock.execute(150)
            }

            val token = KlaviyoLifecycleMonitor.runWithCurrentOrNextActivity(
                timeout = 100,
                onTimeout = { timedOut = true }
            ) { called = true }

            // A settled wait has nothing left to abandon, so handing back a token would let a
            // caller treat it as still pending.
            assertEquals(null, token)
            assert(timedOut) { "Fallback should have run" }
            assert(!called) { "Job should not run once the timeout claimed the outcome" }
            registeredObserver?.let {
                verify { KlaviyoLifecycleMonitor.offActivityEvent(it) }
            }
        } finally {
            unmockkObject(KlaviyoLifecycleMonitor)
            registeredObserver?.let { KlaviyoLifecycleMonitor.offActivityEvent(it) }
        }
    }

    @Test
    fun `cancelling the returned token after the job ran reports failure`() {
        val token = KlaviyoLifecycleMonitor.runWithCurrentOrNextActivity(timeout = 100) { }

        KlaviyoLifecycleMonitor.onActivityResumed(mockk())

        assert(token?.cancel() == false) { "Cancelling a settled job should report failure" }
    }
}

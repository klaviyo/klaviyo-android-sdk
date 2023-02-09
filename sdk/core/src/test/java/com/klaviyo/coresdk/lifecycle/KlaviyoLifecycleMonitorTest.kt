package com.klaviyo.coresdk.lifecycle

import com.klaviyo.coresdk.Registry
import io.mockk.mockk
import io.mockk.unmockkObject
import org.junit.Assert.assertEquals
import org.junit.Test

class KlaviyoLifecycleMonitorTest {

    @Test
    fun `Is registered service`() {
        unmockkObject(Registry)
        assertEquals(KlaviyoLifecycleMonitor, Registry.lifecycleMonitor)
    }

    @Test
    fun `Callbacks are invoked when all activities are stopped`() {
        var callCount = 0

        KlaviyoLifecycleMonitor.onAllActivitiesStopped {
            callCount++
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
}

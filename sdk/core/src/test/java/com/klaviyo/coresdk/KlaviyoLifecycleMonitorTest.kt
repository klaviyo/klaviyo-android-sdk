package com.klaviyo.coresdk

import io.mockk.mockk
import org.junit.Test

class KlaviyoLifecycleMonitorTest {

    @Test
    fun `Callbacks are invoked when all activities are stopped`() {
        var callCount = 0

        KlaviyoLifecycleMonitor.whenStopped {
            callCount++
        }

        KlaviyoLifecycleMonitor.onActivityStarted(mockk())
        KlaviyoLifecycleMonitor.onActivityStarted(mockk())
        KlaviyoLifecycleMonitor.onActivityStopped(mockk())
        assert(callCount == 0)
        KlaviyoLifecycleMonitor.onActivityStopped(mockk())
        assert(callCount == 1)
    }
}

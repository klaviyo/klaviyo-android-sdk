package com.klaviyo.location

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import org.junit.Assert.assertSame
import org.junit.Test

internal class GeofencingNoOpTest : BaseTest() {
    @Test
    fun `registerGeofencing logs error when provider not registered`() {
        val result = Klaviyo.registerGeofencing()
        assertSame(Klaviyo, result)
        verify { spyLog.error(match { it.contains("location module not installed") }, any()) }
    }

    @Test
    fun `unregisterGeofencing logs error when provider not registered`() {
        val result = Klaviyo.unregisterGeofencing()
        assertSame(Klaviyo, result)
        verify { spyLog.error(match { it.contains("location module not installed") }, any()) }
    }
}

package com.klaviyo.coresdk.config

import org.junit.Assert
import org.junit.Test

class SystemClockTest {
    @Test
    fun `Clock uses proper date format`() {
        val regex7 = "^\\d{4}(-\\d\\d(-\\d\\d(T\\d\\d:\\d\\d(:\\d\\d)?(\\.\\d+)?(([+-]\\d\\d:*\\d\\d)|Z)?)?)?)?\$".toRegex()
        val dateString = SystemClock.currentTimeAsString()
        assert(regex7.matches(dateString))
    }

    @Test
    fun `Clock can perform or cancel a delayed task`() {
        var counter = 0

        SystemClock.schedule(1L) { counter++ }
        SystemClock.schedule(1L) { counter++ }.cancel()
        Assert.assertEquals(0, counter)
        Thread.sleep(10L)
        Assert.assertEquals(1, counter)
        Thread.sleep(10L)
        Assert.assertEquals(1, counter)
    }
}

package com.klaviyo.core.config

import org.junit.Assert
import org.junit.Test

class SystemClockTest {

    @Test
    fun `Clock uses proper date format`() {
        val regex7 = "^\\d{4}(-\\d\\d(-\\d\\d(T\\d\\d:\\d\\d(:\\d\\d)?(\\.\\d+)?(([+-]\\d\\d:*\\d\\d)|Z)?)?)?)?\$".toRegex()
        val dateString = SystemClock.isoTime()
        assert(regex7.matches(dateString))
    }

    @Test
    fun `Clock can perform or cancel a delayed task`() {
        var counter = 0

        SystemClock.schedule(5L) { counter++ }
        SystemClock.schedule(5L) { counter++ }.cancel()
        Assert.assertEquals(0, counter)
        Thread.sleep(20L)
        Assert.assertEquals(1, counter)
        Thread.sleep(20L)
        Assert.assertEquals(1, counter)
    }

    @Test
    fun `Clock can perform a task immediately`() {
        var counter = 0

        val task = SystemClock.schedule(5L) { counter++ }
        Assert.assertEquals(0, counter)
        task.runNow()
        Assert.assertEquals(1, counter)
        Thread.sleep(20L)
        Assert.assertEquals(1, counter)
    }
}

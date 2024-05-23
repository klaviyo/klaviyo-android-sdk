package com.klaviyo.core.config

import org.junit.Assert
import org.junit.Test

class SystemClockTest {

    @Test
    fun `Clock uses proper date format`() {
        val systemClock = SystemClock()
        val regex7 = "^\\d{4}(-\\d\\d(-\\d\\d(T\\d\\d:\\d\\d(:\\d\\d)?(\\.\\d+)?(([+-]\\d\\d:*\\d\\d)|Z)?)?)?)?\$".toRegex()
        val dateString = systemClock.isoTime()
        assert(regex7.matches(dateString))
    }

    @Test
    fun `Clock can perform or cancel a delayed task`() {
        val systemClock = SystemClock()
        var counter = 0

        systemClock.schedule(5L) { counter++ }
        systemClock.schedule(5L) { counter++ }.cancel()
        Assert.assertEquals(0, counter)
        Thread.sleep(20L)
        Assert.assertEquals(1, counter)
        Thread.sleep(20L)
        Assert.assertEquals(1, counter)
    }

    @Test
    fun `Clock can perform a task immediately`() {
        val systemClock = SystemClock()
        var counter = 0

        val task = systemClock.schedule(5L) { counter++ }
        Assert.assertEquals(0, counter)
        task.runNow()
        Assert.assertEquals(1, counter)
        Thread.sleep(20L)
        Assert.assertEquals(1, counter)
    }
}

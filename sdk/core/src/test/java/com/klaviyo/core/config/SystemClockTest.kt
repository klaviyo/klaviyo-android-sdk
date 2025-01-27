package com.klaviyo.core.config

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class SystemClockTest {

    private var defaultLocale: Locale? = null

    @Before
    fun setUp() {
        // Save the original default locale
        defaultLocale = Locale.getDefault()

        // Set default locale to Arabic
        Locale.setDefault(Locale("ar"))
    }

    @After
    fun tearDown() {
        // Restore the original default locale after test
        defaultLocale?.let { Locale.setDefault(it) }
    }

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

    @Test
    fun `Clock uses English locale for date formatting`() {
        val expectedFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        // Use a known timestamp for test consistency
        val testTimestamp = 0L
        val expectedOutput = expectedFormat.format(Date(testTimestamp))

        // using the utility
        val actualOutput = SystemClock.isoTime(testTimestamp)

        assertEquals(expectedOutput, actualOutput)
    }
}

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

    private val epochMillis = 1_791_313_200_000L

    @Test
    fun `parseRfc3339 parses second precision in UTC`() {
        assertEquals(epochMillis, SystemClock.parseRfc3339("2026-10-06T19:00:00Z"))
    }

    @Test
    fun `parseRfc3339 truncates fractional seconds to milliseconds`() {
        assertEquals(epochMillis + 123, SystemClock.parseRfc3339("2026-10-06T19:00:00.123Z"))
        assertEquals(
            epochMillis + 123,
            SystemClock.parseRfc3339("2026-10-06T19:00:00.123456+00:00")
        )
        assertEquals(epochMillis + 500, SystemClock.parseRfc3339("2026-10-06T19:00:00.5Z"))
    }

    @Test
    fun `parseRfc3339 applies numeric offsets`() {
        assertEquals(epochMillis, SystemClock.parseRfc3339("2026-10-06T15:00:00-04:00"))
        assertEquals(epochMillis, SystemClock.parseRfc3339("2026-10-07T00:30:00+05:30"))
    }

    @Test
    fun `parseRfc3339 accepts lowercase separators and a negative zero offset`() {
        assertEquals(epochMillis, SystemClock.parseRfc3339("2026-10-06t19:00:00z"))
        assertEquals(epochMillis, SystemClock.parseRfc3339("2026-10-06T19:00:00-00:00"))
    }

    @Test
    fun `parseRfc3339 is available to Clock implementations that do not override it`() {
        val clock = object : Clock {
            override fun currentTimeMillis() = 0L
            override fun isoTime(milliseconds: Long) = ""
            override fun schedule(delay: Long, task: () -> Unit) = SystemClock.schedule(delay, task)
        }

        assertEquals(epochMillis, clock.parseRfc3339("2026-10-06T19:00:00Z"))
    }

    @Test
    fun `parseRfc3339 does not depend on the default time zone`() {
        val defaultZone = TimeZone.getDefault()
        try {
            // 02:30 on this date falls in New York's spring-forward gap
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

            assertEquals(1_772_937_000_000L, SystemClock.parseRfc3339("2026-03-08T02:30:00Z"))
            assertEquals(1_772_955_000_000L, SystemClock.parseRfc3339("2026-03-08T02:30:00-05:00"))
        } finally {
            TimeZone.setDefault(defaultZone)
        }
    }

    @Test
    fun `parseRfc3339 does not depend on the default locale`() {
        // setUp sets an Arabic default locale, which formats digits differently
        assertEquals(epochMillis, SystemClock.parseRfc3339("2026-10-06T19:00:00Z"))
    }

    @Test
    fun `parseRfc3339 returns null for missing or invalid timestamps`() {
        listOf(
            null,
            "",
            "yesterday",
            "2026-10-06 19:00:00",
            "2026-10-06T19:00:00",
            "2026-13-06T19:00:00Z",
            "1791313200",
            "2026-02-30T19:00:00Z",
            "2026-10-06T24:00:00Z",
            "2026-10-06T19:00:00+24:00",
            "2026-10-06T19:00:00+05:60"
        ).forEach { Assert.assertNull(it, SystemClock.parseRfc3339(it)) }
    }
}

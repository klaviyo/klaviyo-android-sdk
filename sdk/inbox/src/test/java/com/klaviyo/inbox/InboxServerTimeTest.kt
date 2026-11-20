package com.klaviyo.inbox

import com.klaviyo.fixtures.BaseTest
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

internal class InboxServerTimeTest : BaseTest() {
    private val originalZone = TimeZone.getDefault()

    @After
    fun restoreZone() = TimeZone.setDefault(originalZone)

    @Test
    fun `a fixed instant formats as UTC with milliseconds and a literal offset`() {
        assertEquals("2026-10-15T12:00:00.123+00:00", InboxServerTime.format(SENT_AT_MS + 123))
        assertEquals("1970-01-01T00:00:00.000+00:00", InboxServerTime.format(0))
    }

    @Test
    fun `the device zone does not change the output`() {
        val utc = InboxServerTime.format(SENT_AT_MS)

        listOf("America/Los_Angeles", "Asia/Kolkata", "Pacific/Chatham").forEach {
            TimeZone.setDefault(TimeZone.getTimeZone(it))
            assertEquals(it, utc, InboxServerTime.format(SENT_AT_MS))
        }
    }

    @Test
    fun `the output round trips through the clock parser`() {
        listOf(0L, SENT_AT_MS, SENT_AT_MS + 987, 1_791_313_200_001L).forEach {
            assertEquals(it, staticClock.parseRfc3339(InboxServerTime.format(it)))
        }
    }

    private companion object {
        const val SENT_AT_MS = InboxServerFixtures.SENT_AT_MS
    }
}

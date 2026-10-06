package com.klaviyo.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class Rfc3339Test {

    private val epochMillis = 1_791_313_200_000L

    @Test
    fun `parses second precision in UTC`() {
        assertEquals(epochMillis, Rfc3339.parseMillis("2026-10-06T19:00:00Z"))
    }

    @Test
    fun `parses millisecond and microsecond precision, truncating to milliseconds`() {
        assertEquals(epochMillis + 123, Rfc3339.parseMillis("2026-10-06T19:00:00.123Z"))
        assertEquals(epochMillis + 123, Rfc3339.parseMillis("2026-10-06T19:00:00.123456+00:00"))
        assertEquals(epochMillis + 500, Rfc3339.parseMillis("2026-10-06T19:00:00.5Z"))
    }

    @Test
    fun `applies numeric offsets`() {
        assertEquals(epochMillis, Rfc3339.parseMillis("2026-10-06T15:00:00-04:00"))
        assertEquals(epochMillis, Rfc3339.parseMillis("2026-10-07T00:30:00+05:30"))
    }

    @Test
    fun `accepts lowercase separators and a negative zero offset`() {
        assertEquals(epochMillis, Rfc3339.parseMillis("2026-10-06t19:00:00z"))
        assertEquals(epochMillis, Rfc3339.parseMillis("2026-10-06T19:00:00-00:00"))
    }

    @Test
    fun `returns null for missing or invalid timestamps`() {
        assertNull(Rfc3339.parseMillis(null))
        assertNull(Rfc3339.parseMillis(""))
        assertNull(Rfc3339.parseMillis("yesterday"))
        assertNull(Rfc3339.parseMillis("2026-10-06 19:00:00"))
        assertNull(Rfc3339.parseMillis("2026-10-06T19:00:00"))
        assertNull(Rfc3339.parseMillis("2026-13-06T19:00:00Z"))
        assertNull(Rfc3339.parseMillis("1791313200"))
        assertNull(Rfc3339.parseMillis("2026-02-30T19:00:00Z"))
        assertNull(Rfc3339.parseMillis("2026-10-06T19:00:00+24:00"))
        assertNull(Rfc3339.parseMillis("2026-10-06T19:00:00+05:60"))
    }
}

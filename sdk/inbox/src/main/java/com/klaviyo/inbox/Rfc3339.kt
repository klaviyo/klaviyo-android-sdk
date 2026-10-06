package com.klaviyo.inbox

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Parses RFC 3339 timestamps such as `2026-10-06T19:00:00Z` or
 * `2026-10-06T19:00:00.123456+00:00`. Fractional seconds beyond milliseconds are truncated.
 */
internal object Rfc3339 {
    private const val MILLIS_DIGITS = 3
    private const val MINUTES_PER_HOUR = 60
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MAX_OFFSET_HOURS = 23
    private const val MAX_OFFSET_MINUTES = 59
    private const val UTC = "UTC"

    private val pattern = Regex(
        """(\d{4})-(\d{2})-(\d{2})[Tt](\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(?:([Zz])|([+-])(\d{2}):(\d{2}))"""
    )

    /**
     * @return Epoch milliseconds, or null when [value] is null or not a valid RFC 3339 timestamp
     */
    fun parseMillis(value: String?): Long? {
        val match = value?.let { pattern.matchEntire(it.trim()) } ?: return null
        val groups = match.groupValues
        return runCatching {
            val calendar = GregorianCalendar(TimeZone.getTimeZone(UTC)).apply {
                isLenient = false
                clear()
                set(
                    groups[1].toInt(),
                    groups[2].toInt() - 1,
                    groups[3].toInt(),
                    groups[4].toInt(),
                    groups[5].toInt(),
                    groups[6].toInt()
                )
                set(
                    Calendar.MILLISECOND,
                    groups[7].take(MILLIS_DIGITS).padEnd(MILLIS_DIGITS, '0').toInt()
                )
            }
            val offsetMinutes = if (groups[8].isNotEmpty()) {
                0
            } else {
                val hours = groups[10].toInt()
                val minutes = groups[11].toInt()
                require(hours <= MAX_OFFSET_HOURS && minutes <= MAX_OFFSET_MINUTES)
                val sign = if (groups[9] == "-") -1 else 1
                sign * (hours * MINUTES_PER_HOUR + minutes)
            }
            calendar.timeInMillis - offsetMinutes * MILLIS_PER_MINUTE
        }.getOrNull()
    }
}

package com.klaviyo.inbox

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formats the `changed_at` timestamp of a state change.
 *
 * Milliseconds keep the order of changes sent within one second. The `+00:00` suffix is literal
 * because `XXX` needs API 24 and `Z` would give `+0000`, which is not RFC 3339.
 */
internal object InboxServerTime {
    private const val PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'+00:00'"
    private const val UTC = "UTC"

    /**
     * `SimpleDateFormat` is not thread safe, so each call builds its own instance.
     */
    @SuppressLint("SimpleDateFormat")
    fun format(epochMillis: Long): String = SimpleDateFormat(PATTERN, Locale.US).apply {
        timeZone = TimeZone.getTimeZone(UTC)
    }.format(Date(epochMillis))
}

package com.klaviyo.core.config

import android.annotation.SuppressLint
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * RFC 3339 parsing that works on every supported API level
 * @see Clock.parseRfc3339
 */
internal object Rfc3339 {
    private const val MILLIS_DIGITS = 3
    private const val MAX_OFFSET_HOURS = 23
    private const val MAX_OFFSET_MINUTES = 59
    private const val UTC_OFFSET = "+0000"

    private val pattern = Regex(
        """(\d{4}-\d{2}-\d{2})[Tt](\d{2}:\d{2}:\d{2})(?:\.(\d+))?(?:[Zz]|([+-]\d{2}):(\d{2}))"""
    )

    /**
     * Strict parser for the shape produced by [normalize].
     * `SimpleDateFormat` is not thread safe, so each parse builds its own instance.
     */
    @SuppressLint("SimpleDateFormat")
    private fun parser() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ENGLISH).apply {
        isLenient = false
        // The parsed offset sets the instant; a UTC calendar keeps device DST rules out of validation
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun parseMillis(timestamp: String?): Long? {
        val normalized = timestamp?.let(::normalize) ?: return null
        val position = ParsePosition(0)
        return parser().parse(normalized, position)
            ?.takeIf { position.index == normalized.length }
            ?.time
    }

    /**
     * Rewrites RFC 3339 into a fixed shape `SimpleDateFormat` can parse on every API level:
     * fraction to exactly three digits, and `Z` or `+HH:MM` to `+HHMM`.
     * Offset ranges are checked here because `SimpleDateFormat` accepts any digits in `Z`.
     */
    private fun normalize(timestamp: String): String? {
        val match = pattern.matchEntire(timestamp.trim()) ?: return null
        val (date, time, fraction, offsetHours, offsetMinutes) = match.destructured
        val millis = fraction.take(MILLIS_DIGITS).padEnd(MILLIS_DIGITS, '0')
        val offset = if (offsetHours.isEmpty()) {
            UTC_OFFSET
        } else {
            if (offsetHours.drop(1).toInt() > MAX_OFFSET_HOURS ||
                offsetMinutes.toInt() > MAX_OFFSET_MINUTES
            ) {
                return null
            }
            offsetHours + offsetMinutes
        }
        return "${date}T$time.$millis$offset"
    }
}

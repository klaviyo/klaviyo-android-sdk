package com.klaviyo.core.config

import android.annotation.SuppressLint
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Timer
import kotlin.concurrent.schedule

internal object SystemClock : Clock {

    // Explicitly set Locale.ENGLISH to ensure consistent formatting
    @SuppressLint("SimpleDateFormat")
    private val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Strict parser for the normalized form produced by [normalizeRfc3339].
     * `SimpleDateFormat` is not thread safe, so each parse builds its own instance.
     */
    @SuppressLint("SimpleDateFormat")
    private fun rfc3339Parser() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ENGLISH).apply {
        isLenient = false
    }

    private val rfc3339Pattern = Regex(
        """(\d{4}-\d{2}-\d{2})[Tt](\d{2}:\d{2}:\d{2})(?:\.(\d+))?(?:[Zz]|([+-]\d{2}):(\d{2}))"""
    )

    private const val MILLIS_DIGITS = 3
    private const val MAX_OFFSET_HOURS = 23
    private const val MAX_OFFSET_MINUTES = 59
    private const val UTC_OFFSET = "+0000"

    /**
     * Rewrites RFC 3339 into a fixed shape `SimpleDateFormat` can parse on every API level:
     * fraction to exactly three digits, and `Z` or `+HH:MM` to `+HHMM`.
     * Offset ranges are checked here because `SimpleDateFormat` accepts any digits in `Z`.
     */
    private fun normalizeRfc3339(timestamp: String): String? {
        val match = rfc3339Pattern.matchEntire(timestamp.trim()) ?: return null
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

    override fun parseRfc3339(timestamp: String?): Long? {
        val normalized = timestamp?.let(::normalizeRfc3339) ?: return null
        val position = ParsePosition(0)
        return rfc3339Parser().parse(normalized, position)
            ?.takeIf { position.index == normalized.length }
            ?.time
    }

    override fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }

    override fun isoTime(milliseconds: Long): String {
        return format.format(Date(milliseconds))
    }

    override fun schedule(delay: Long, task: () -> Unit): Clock.Cancellable = Timer()
        .schedule(delay) { task() }.let { timer ->
            return object : Clock.Cancellable {
                override fun runNow() = task().also { timer.cancel() }
                override fun cancel() = timer.cancel()
            }
        }
}

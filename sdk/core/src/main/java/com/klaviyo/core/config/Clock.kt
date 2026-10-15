package com.klaviyo.core.config

interface Clock {

    fun currentTimeMillis(): Long

    fun isoTime(milliseconds: Long = currentTimeMillis()): String

    /**
     * Parse an RFC 3339 timestamp, such as `2026-10-06T19:00:00Z` or
     * `2026-10-06T19:00:00.123456+05:30`, to epoch milliseconds.
     * Fractional seconds beyond milliseconds are truncated.
     *
     * @return Epoch milliseconds, or null when [timestamp] is null or not valid RFC 3339
     */
    fun parseRfc3339(timestamp: String?): Long?

    fun schedule(delay: Long, task: () -> Unit): Cancellable

    interface Cancellable {
        fun runNow()
        fun cancel(): Boolean
    }
}

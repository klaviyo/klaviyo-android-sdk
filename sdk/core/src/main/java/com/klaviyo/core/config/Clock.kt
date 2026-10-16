package com.klaviyo.core.config

interface Clock {

    fun currentTimeMillis(): Long

    /** Monotonic time for measuring elapsed intervals. */
    fun monotonicTimeMillis(): Long = System.nanoTime() / 1_000_000

    fun isoTime(milliseconds: Long = currentTimeMillis()): String

    fun schedule(delay: Long, task: () -> Unit): Cancellable

    interface Cancellable {
        fun runNow()
        fun cancel(): Boolean
    }
}

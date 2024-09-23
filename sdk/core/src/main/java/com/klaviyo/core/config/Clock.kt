package com.klaviyo.core.config

interface Clock {

    fun currentTimeMillis(): Long

    fun isoTime(milliseconds: Long = currentTimeMillis()): String

    fun schedule(delay: Long, task: () -> Unit): Cancellable

    interface Cancellable {
        fun runNow()
        fun cancel(): Boolean
    }
}

package com.klaviyo.coresdk.config

interface Clock {

    fun currentTimeMillis(): Long

    fun currentTimeAsString(): String

    fun schedule(delay: Long, task: () -> Unit): Cancellable

    interface Cancellable {
        fun cancel(): Boolean
    }
}

package com.klaviyo.coresdk.config

/**
 * Implementation of Clock for unit tests
 *
 * @property time
 */
internal class StaticClock(private val time: Long, private val formatted: String) : Clock {
    override fun currentTimeMillis(): Long = time
    override fun currentTimeAsString(): String = formatted
}

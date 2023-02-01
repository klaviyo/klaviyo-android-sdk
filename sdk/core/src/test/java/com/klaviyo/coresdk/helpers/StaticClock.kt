package com.klaviyo.coresdk.helpers

/**
 * Implementation of Clock for unit tests
 *
 * @property time
 */
internal class StaticClock(private val time: Long) : Clock {
    override fun currentTimeMillis(): Long = time
}

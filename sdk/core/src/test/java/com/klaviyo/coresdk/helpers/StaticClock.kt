package com.klaviyo.coresdk.helpers

import com.klaviyo.coresdk.networking.Clock

internal class StaticClock(
    private val time: Long
) : Clock {
    override fun currentTimeMillis(): Long = time
}

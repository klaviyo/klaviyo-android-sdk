package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.networking.Clock

internal class StaticClock(
    private val time: Long
) : Clock {
    override fun currentTimeMillis(): Long = time
}

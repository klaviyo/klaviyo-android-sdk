package com.klaviyo.coresdk.helpers

internal object SystemClock : Clock {

    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

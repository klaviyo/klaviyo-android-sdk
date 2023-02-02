package com.klaviyo.coresdk.config

internal object SystemClock : Clock {

    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

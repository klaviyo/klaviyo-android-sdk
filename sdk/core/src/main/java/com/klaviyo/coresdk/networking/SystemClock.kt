package com.klaviyo.coresdk.networking

internal object SystemClock : Clock {

    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

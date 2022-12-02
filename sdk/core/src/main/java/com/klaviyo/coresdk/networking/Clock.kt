package com.klaviyo.coresdk.networking

internal interface Clock {

    fun currentTimeMillis(): Long
}

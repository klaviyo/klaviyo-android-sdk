package com.klaviyo.coresdk.config

interface Clock {

    fun currentTimeMillis(): Long

    fun currentTimeAsString(): String
}

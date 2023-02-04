package com.klaviyo.coresdk.config

import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

internal object SystemClock : Clock {

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    private val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun currentTimeAsString(): String = format.format(Date(currentTimeMillis()))
}

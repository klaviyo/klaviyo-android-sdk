package com.klaviyo.coresdk.config

import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

internal object SystemClock : Clock {

    private val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }

    override fun currentTimeAsString(): String {
        return format.format(Date(currentTimeMillis()))
    }
}

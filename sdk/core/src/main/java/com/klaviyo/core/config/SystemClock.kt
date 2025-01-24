package com.klaviyo.core.config

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Timer
import kotlin.concurrent.schedule

internal object SystemClock : Clock {

    // Explicitly set Locale.ENGLISH to ensure consistent formatting
    @SuppressLint("SimpleDateFormat")
    private val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }

    override fun isoTime(milliseconds: Long): String {
        return format.format(Date(milliseconds))
    }

    override fun schedule(delay: Long, task: () -> Unit): Clock.Cancellable = Timer()
        .schedule(delay) { task() }.let { timer ->
            return object : Clock.Cancellable {
                override fun runNow() = task().also { timer.cancel() }
                override fun cancel() = timer.cancel()
            }
        }
}

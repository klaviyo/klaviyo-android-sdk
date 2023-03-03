package com.klaviyo.core

import android.util.Log

/**
 * Android Log output wrapper
 */
object Console {
    enum class Level(val call: (String, String, Throwable?) -> Unit) {
        Debug(Log::d), Info(Log::i), Error(Log::e), Assert(Log::wtf)
    }

    fun log(msg: String, level: Level, tag: String, ex: Throwable? = null) {
        level.call(tag, msg, ex)
    }
}

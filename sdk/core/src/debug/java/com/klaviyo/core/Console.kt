package com.klaviyo.core

import android.util.Log

/**
 * Android Log output wrapper
 */
internal object Console {
    enum class Level {
        Debug, Info, Error, Assert
    }

    fun log(msg: String, level: Level, tag: String, ex: Throwable? = null) = when (level) {
        Level.Debug -> ex?.let { Log.d(tag, msg, ex) } ?: Log.d(tag, msg)
        Level.Info -> ex?.let { Log.i(tag, msg, ex) } ?: Log.i(tag, msg)
        Level.Error -> ex?.let { Log.e(tag, msg, ex) } ?: Log.e(tag, msg)
        Level.Assert -> ex?.let { Log.wtf(tag, msg, ex) } ?: Log.wtf(tag, msg)
    }
}

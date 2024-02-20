package com.klaviyo.core

import android.util.Log
import com.klaviyo.core.config.Log.Level

/**
 * Bridge between the Klaviyo Log levels and Android static log functions
 */
fun Level.log(tag: String, msg: String, ex: Throwable? = null) = when (this) {
    Level.Verbose -> Log.v(tag, msg, ex)
    Level.Debug -> Log.d(tag, msg, ex)
    Level.Info -> Log.i(tag, msg, ex)
    Level.Warning -> Log.w(tag, msg, ex)
    Level.Error -> Log.e(tag, msg, ex)
    Level.Assert -> Log.wtf(tag, msg, ex)
    Level.None -> 0
}

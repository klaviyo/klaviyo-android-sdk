package com.klaviyo.core.networking

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

/**
 * Abstraction of our interactions with handlers/threads for isolation purposes
 */
object HandlerUtil {
    fun getHandler(looper: Looper) = Handler(looper)
    fun getHandlerThread(name: String?) = HandlerThread(name)
}

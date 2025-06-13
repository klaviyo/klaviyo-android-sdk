package com.klaviyo.core.utils

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

/**
 * Abstraction of our interactions with handlers/threads for isolation purposes
 */
interface ThreadHelper {
    fun getHandler(looper: Looper): Handler
    fun getHandlerThread(name: String?): HandlerThread
    fun runOnUiThread(job: () -> Unit)
}

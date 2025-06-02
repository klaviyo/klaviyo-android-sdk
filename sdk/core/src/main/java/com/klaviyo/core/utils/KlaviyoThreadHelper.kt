package com.klaviyo.core.utils

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

/**
 * Abstraction of our interactions with handlers/threads for isolation purposes
 * @deprecated Use [com.klaviyo.core.Registry.threadHelper] instead
 */
object KlaviyoThreadHelper : ThreadHelper {
    override fun getHandler(looper: Looper) = Handler(looper)
    override fun getHandlerThread(name: String?) = HandlerThread(name)
    override fun runOnUiThread(job: () -> Unit) {
        val mainLooper = Looper.getMainLooper()

        if (mainLooper == Looper.myLooper()) {
            // Already on main thread, run immediately
            job()
        } else {
            // Post to main thread
            getHandler(mainLooper).post(job)
        }
    }
}

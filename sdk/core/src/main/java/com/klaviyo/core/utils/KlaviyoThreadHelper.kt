package com.klaviyo.core.utils

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

/**
 * Abstraction of our interactions with handlers/threads for isolation purposes
 * @see com.klaviyo.core.Registry.threadHelper
 */
internal object KlaviyoThreadHelper : ThreadHelper {
    override fun getHandler(looper: Looper) = Handler(looper)
    override fun getHandlerThread(name: String?) = HandlerThread(name)
    override fun isMainThread(): Boolean = Looper.getMainLooper() == Looper.myLooper()

    override fun runOnUiThread(job: () -> Unit) {
        val mainLooper = Looper.getMainLooper()

        if (isMainThread()) {
            // Already on main thread, run immediately
            job()
        } else {
            // Post to main thread
            getHandler(mainLooper).post(job)
        }
    }
}

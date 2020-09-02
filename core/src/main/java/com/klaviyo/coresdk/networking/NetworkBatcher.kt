package com.klaviyo.coresdk.networking

import android.os.Handler
import android.os.HandlerThread
import com.klaviyo.coresdk.KlaviyoConfig
import java.util.concurrent.ConcurrentLinkedQueue

object NetworkBatcher {
    private val handlerThread = HandlerThread("Klaviyo Network Batcher")
    private var handler: Handler? = null
    private var batchQueue = ConcurrentLinkedQueue<NetworkRequest>()
    private var queueInitTime = 0L

    internal fun initBatcher() {
        if (!handlerThread.isAlive) {
            handlerThread.start()
            handler = Handler(handlerThread.looper)
        }
        handler?.post(NetworkRunnable())
    }

    internal fun forceEmptyQueue() {
        handler?.removeCallbacksAndMessages(null)
        handler?.post(NetworkRunnable(true))
    }

    internal fun getBatchQueueSize(): Int {
        return batchQueue.size
    }

    internal fun batchRequests(vararg requests: NetworkRequest) {
        if (batchQueue.isEmpty()) {
            initBatcher()
            queueInitTime = System.currentTimeMillis()

        }

        for (request in requests) {
            if (request is TrackRequest) {
                request.generateUnixTimestamp()
            }
            batchQueue.offer(request)
        }
    }

    internal class NetworkRunnable(private val forceEmpty: Boolean = false): Runnable {
        override fun run() {
            val emptied = emptyRequestQueue(forceEmpty)
            if (!emptied) {
                handler?.postDelayed(this, KlaviyoConfig.networkFlushCheckInterval)
            }
        }

        private fun emptyRequestQueue(forceEmpty: Boolean = false): Boolean {
            val queueTimePassed = System.currentTimeMillis() - queueInitTime
            val readyToEmpty = batchQueue.size >= KlaviyoConfig.networkFlushDepth || queueTimePassed >= KlaviyoConfig.networkFlushInterval
            if (forceEmpty || readyToEmpty) {
                var request: NetworkRequest? = null
                while(batchQueue.poll().also { request = it } != null) {
                    request?.sendNetworkRequest()
                }
                return true
            }
            return false
        }
    }
}
package com.klaviyo.coresdk.networking

import android.os.Handler
import android.os.HandlerThread
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.requests.NetworkRequest
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Class for handling a simple batcher for grouping up network requests
 */
object NetworkBatcher {
    private val handlerThread = HandlerThread("Klaviyo Network Batcher")
    private var handler: Handler? = null
    private var batchQueue = ConcurrentLinkedQueue<NetworkRequest>()
    private var queueInitTime = 0L

    /**
     * Initialize the network batcher by kicking off the thread that it runs on
     */
    internal fun initBatcher() {
        if (!handlerThread.isAlive) {
            handlerThread.start()
            handler = Handler(handlerThread.looper)
        }
        handler?.post(NetworkRunnable())
    }

    /**
     * Empties all queued tasks from the network batching thread
     */
    internal fun forceEmptyQueue() {
        handler?.removeCallbacksAndMessages(null)
        handler?.post(NetworkRunnable(true))
    }

    /**
     * Gets the current size of the batch queue
     *
     * @return number of requests in the batch queue
     */
    internal fun getBatchQueueSize(): Int {
        return batchQueue.size
    }

    /**
     * Inserts a variable amount of [NetworkRequest] objects into the batch queue
     * Initializes the batch queue if it has not already been initialized
     */
    internal fun batchRequests(vararg requests: NetworkRequest) {
        if (batchQueue.isEmpty()) {
            initBatcher()
            queueInitTime = System.currentTimeMillis()
        }

        for (request in requests) {
            batchQueue.offer(request)
        }
    }

    /**
     * Runnable which executes the network batcher.
     * As long as there are items in the queue the thread will loop.
     * When the queue is cleared the thread will not loop itself and will terminate.
     *
     * @property forceEmpty Boolean that will force the batcher to empty its queued requests instantly
     */
    internal class NetworkRunnable(private val forceEmpty: Boolean = false) : Runnable {
        override fun run() {
            val emptied = emptyRequestQueue(forceEmpty)
            if (!emptied) {
                handler?.postDelayed(this, KlaviyoConfig.networkFlushCheckInterval.toLong())
            }
        }

        /**
         * Empties the batcher of all queued requests by instantly sending each request to Klaviyo
         * The queue will empty whenever the triggers specified by the [KlaviyoConfig] are met
         *
         * @param forceEmpty Overrides the typical queue emptying triggers to force the queue to empty itself
         *
         * @return Whether the request queue was emptied or not
         */
        private fun emptyRequestQueue(forceEmpty: Boolean = false): Boolean {
            val queueTimePassed = System.currentTimeMillis() - queueInitTime
            val readyToEmpty = batchQueue.size >= KlaviyoConfig.networkFlushDepth || queueTimePassed >= KlaviyoConfig.networkFlushInterval
            if (forceEmpty || readyToEmpty) {
                var request: NetworkRequest? = null
                while (batchQueue.poll().also { request = it } != null) {
                    request?.sendNetworkRequest()
                }
                return true
            }
            return false
        }
    }
}

package com.klaviyo.coresdk.networking

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.klaviyo.coresdk.Registry
import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.networking.requests.IdentifyApiRequest
import com.klaviyo.coresdk.networking.requests.KlaviyoApiRequest
import com.klaviyo.coresdk.networking.requests.TrackApiRequest
import java.util.concurrent.ConcurrentLinkedQueue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Coordinator of API request traffic
 */
internal object KlaviyoApiClient : ApiClient {
    internal const val QUEUE_KEY = "klaviyo_api_request_queue"

    private val handlerThread = HandlerUtil.getHandlerThread(KlaviyoApiClient::class.simpleName)
    private var handler: Handler? = null
    private var apiQueue = ConcurrentLinkedQueue<KlaviyoApiRequest>()

    init {
        startListeners()
        restoreQueue()
    }

    fun startListeners() {
        // Flush queue immediately when app stops
        Registry.lifecycleMonitor.onAllActivitiesStopped {
            flushQueue()
        }

        // Flush queue when network connection is restored
        Registry.networkMonitor.onNetworkChange { isOnline ->
            if (isOnline) flushQueue()
        }
    }

    override fun enqueueProfile(profile: Profile) {
        enqueueRequest(IdentifyApiRequest(profile))
    }

    override fun enqueueEvent(event: Event, profile: Profile) {
        enqueueRequest(TrackApiRequest(event, profile))
    }

    /**
     * Enqueues an [KlaviyoApiRequest] to run in the background
     * These requests are sent to the Klaviyo asynchronous APIs
     *
     * This method will initialize the API queue and the batching thread
     * if this is the first request made since launch.
     */
    fun enqueueRequest(vararg requests: KlaviyoApiRequest) {
        if (apiQueue.isEmpty()) {
            initBatch()
        }

        for (request in requests) {
            if (!apiQueue.contains(request)) {
                apiQueue.offer(request)
            }
            Registry.dataStore.store(request.uuid, request.toJson())
        }

        Registry.dataStore.store(
            QUEUE_KEY,
            JSONArray(apiQueue.map { it.uuid }).toString()
        )
    }

    /**
     * Awaken queued requests from persistent store
     */
    fun restoreQueue() {
        // TODO JSON type safety checks
        Registry.dataStore.fetch(QUEUE_KEY)?.let {
            val queue = JSONArray(it)
            Array(queue.length()) { i -> queue.optString(i) }
        }?.map { uuid ->
            Registry.dataStore.fetch(uuid)?.let { json ->
                val jsonObject = JSONObject(json)
                val request = KlaviyoApiRequest.fromJson(jsonObject)
                enqueueRequest(request)
            }
        }
    }

    /**
     * Initialize the network queue batching with a thread to run on
     */
    private fun initBatch() {
        if (!handlerThread.isAlive) {
            handlerThread.start()
            handler = HandlerUtil.getHandler(handlerThread.looper)
        }

        handler?.post(NetworkRunnable())
    }

    /**
     * Gets the current size of the API queue
     *
     * @return number of requests in the batch queue
     */
    fun getQueueSize(): Int = apiQueue.size

    /**
     * Force the API queue to send immediately instead of waiting on the configured criteria
     */
    private fun flushQueue() {
        handler?.removeCallbacksAndMessages(null)
        handler?.post(NetworkRunnable(true))
    }

    /**
     * Runnable which flushes the API queue in batches for efficiency.
     * As long as there are items in the queue, the thread will loop.
     * When the queue is cleared the thread will not loop itself and will terminate.
     *
     * @property force Boolean that will force the queue to flush now
     */
    class NetworkRunnable(val force: Boolean = false) : Runnable {
        private val queueInitTime = Registry.clock.currentTimeMillis()

        private val flushInterval: Long
            get() = Registry.config.networkFlushInterval.toLong()

        private val flushDepth: Int
            get() = Registry.config.networkFlushDepth

        override fun run() {
            val emptied = flushQueue(force)
            if (!emptied) {
                handler?.postDelayed(this, flushInterval)
            }
        }

        /**
         * Empties all queued requests by instantly sending each request to Klaviyo
         * The queue will flush whenever the triggers specified in config are met
         *
         * @param force Overrides the typical timings to force the queue to flush
         *
         * @return Whether the request queue was emptied or not
         */
        private fun flushQueue(force: Boolean = false): Boolean {
            val queueTimePassed = Registry.clock.currentTimeMillis() - queueInitTime

            if (force || getQueueSize() >= flushDepth || queueTimePassed >= flushInterval) {
                while (!apiQueue.isEmpty()) {
                    val apiRequest = apiQueue.poll()
                    apiRequest?.apply {
                        send()
                        Registry.dataStore.clear(uuid)
                    }
                }

                Registry.dataStore.clear(QUEUE_KEY)

                return true
            }

            return false
        }
    }

    /**
     * Abstraction of our interactions with handlers/threads for isolation purposes
     */
    internal object HandlerUtil {
        fun getHandler(looper: Looper) = Handler(looper)
        fun getHandlerThread(name: String?) = HandlerThread(name)
    }
}

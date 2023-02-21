package com.klaviyo.analytics.networking

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.requests.EventApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.ProfileApiRequest
import com.klaviyo.analytics.networking.requests.PushTokenApiRequest
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import java.util.concurrent.ConcurrentLinkedDeque
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Coordinator of API request traffic
 */
internal object KlaviyoApiClient : ApiClient {
    internal const val QUEUE_KEY = "klaviyo_api_request_queue"

    private val handlerThread = HandlerUtil.getHandlerThread(KlaviyoApiClient::class.simpleName)
    private var handler: Handler? = null
    private var apiQueue = ConcurrentLinkedDeque<KlaviyoApiRequest>()

    init {
        // Stop our handler thread when all activities stop
        Registry.lifecycleMonitor.onActivityEvent {
            when (it) {
                is ActivityEvent.AllStopped -> stopBatch()
                else -> Unit
            }
        }

        // Stop the background batching job while offline
        Registry.networkMonitor.onNetworkChange { isOnline ->
            if (isOnline) startBatch(true)
            else stopBatch()
        }

        restoreQueue()
    }

    override fun enqueueProfile(profile: Profile) {
        enqueueRequest(ProfileApiRequest(profile))
    }

    override fun enqueuePushToken(token: String, profile: Profile) {
        enqueueRequest(PushTokenApiRequest(token, profile))
    }

    override fun enqueueEvent(event: Event, profile: Profile) {
        enqueueRequest(EventApiRequest(event, profile))
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

        persistQueue()
    }

    /**
     * Gets the current size of the API queue
     *
     * @return number of requests in the batch queue
     */
    fun getQueueSize(): Int = apiQueue.size

    /**
     * Reset the in-memory queue to the queue from data store
     */
    fun restoreQueue() {
        while (apiQueue.isNotEmpty()) {
            apiQueue.remove()
        }

        // Keep track if there's any errors restoring from persistent store
        var wasMutated = false

        Registry.dataStore.fetch(QUEUE_KEY)?.let {
            try {
                val queue = JSONArray(it)
                Array(queue.length()) { i -> queue.optString(i) }
            } catch (exception: JSONException) {
                wasMutated = true
                emptyArray<String>()
            }
        }?.forEach { uuid ->
            Registry.dataStore.fetch(uuid)?.let { json ->
                try {
                    val request = KlaviyoApiRequest.fromJson(JSONObject(json))
                    if (!apiQueue.contains(request)) {
                        apiQueue.offer(request)
                    }
                } catch (exception: JSONException) {
                    wasMutated = true
                    Registry.dataStore.clear(uuid)
                }
            }
        }

        // If errors were encountered, update persistent store with corrected queue
        if (wasMutated) persistQueue()

        if (apiQueue.isNotEmpty()) initBatch()
    }

    /**
     * Flush current queue to persistent store
     */
    private fun persistQueue() = Registry.dataStore.store(
        QUEUE_KEY,
        JSONArray(apiQueue.map { it.uuid }).toString()
    )

    /**
     * Start a network batch to process the request queue
     */
    private fun initBatch() {
        if (!handlerThread.isAlive) {
            handlerThread.start()
            handler = HandlerUtil.getHandler(handlerThread.looper)
        }

        startBatch()
    }

    /**
     * Start network runner job on the handler thread
     */
    private fun startBatch(force: Boolean = false) {
        stopBatch() // we only ever want one batch job running
        handler?.post(NetworkRunnable(force))
    }

    /**
     * Stop all jobs on our handler thread
     */
    private fun stopBatch() = handler?.removeCallbacksAndMessages(null)

    /**
     * Runnable which flushes the API queue in batches for efficiency.
     * As long as there are items in the queue, the thread will loop.
     * When the queue is cleared the thread will not loop itself and will terminate.
     *
     * @property force Boolean that will force the queue to flush now
     */
    class NetworkRunnable(private var force: Boolean = false) : Runnable {
        private val queueInitTime = Registry.clock.currentTimeMillis()

        private var flushInterval: Long = Registry.config.networkFlushInterval.toLong()

        private val flushDepth: Int = Registry.config.networkFlushDepth

        /**
         * Send queued requests serially
         * The queue will flush whenever the triggers specified in config are met
         * Posts another delayed batch job if requests remains
         */
        override fun run() {
            val queueTimePassed = Registry.clock.currentTimeMillis() - queueInitTime

            if (getQueueSize() < flushDepth && queueTimePassed < flushInterval && !force) {
                return requeue()
            }

            while (apiQueue.isNotEmpty()) {
                val apiRequest = apiQueue.poll()

                when (apiRequest?.send()) {
                    KlaviyoApiRequest.Status.Complete, KlaviyoApiRequest.Status.Failed -> {
                        // On success or absolute failure, remove from queue and persistent store
                        Registry.dataStore.clear(apiRequest.uuid)
                    }
                    KlaviyoApiRequest.Status.PendingRetry -> {
                        // Encountered a retryable error
                        // Put this back on top of the queue and we'll try again with backoff
                        apiQueue.offerFirst(apiRequest)
                        flushInterval *= apiRequest.attempts + 1
                        break
                    }
                    // Offline or at the end of the queue... either way break the loop
                    else -> break
                }
            }

            persistQueue()

            if (!apiQueue.isEmpty()) {
                requeue()
            }
        }

        /**
         * Re-queue the job to run again after [flushInterval] milliseconds
         */
        private fun requeue() {
            force = false
            handler?.postDelayed(this, flushInterval)
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

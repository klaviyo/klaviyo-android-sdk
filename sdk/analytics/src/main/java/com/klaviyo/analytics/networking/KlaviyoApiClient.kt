package com.klaviyo.analytics.networking

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.requests.EventApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest.Status
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

    /**
     * List of registered API observers
     */
    private var apiObservers = mutableListOf<ApiObserver>()

    init {
        onApiRequest { r ->
            when (r.state) {
                Status.Unsent.name -> Registry.log.debug("${r.type} Request enqueued")
                Status.Inflight.name -> Registry.log.debug("${r.type} Request inflight")
                Status.PendingRetry.name -> Registry.log.error("${r.type} Request retrying")
                Status.Complete.name -> Registry.log.info("${r.type} Request completed")
                else -> Registry.log.error("${r.type} Request failed")
            }

            r.responseBody?.let { response ->
                val body = r.requestBody?.let { JSONObject(it).toString(2) }
                Registry.log.verbose("${r.httpMethod}: ${r.url}")
                Registry.log.verbose("Headers: ${r.headers}")
                Registry.log.verbose("Query: ${r.query}")
                Registry.log.verbose("Body: $body")
                Registry.log.verbose("${r.responseCode} $response")
            }
        }

        // Stop our handler thread when all activities stop
        Registry.lifecycleMonitor.onActivityEvent {
            when (it) {
                is ActivityEvent.AllStopped -> stopBatch()
                else -> Unit
            }
        }

        // Stop the background batching job while offline
        Registry.networkMonitor.onNetworkChange { isOnline ->
            if (isOnline) {
                startBatch(true)
            } else {
                stopBatch()
            }
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

    override fun onApiRequest(observer: ApiObserver) {
        apiObservers += observer
    }

    override fun offApiRequest(observer: ApiObserver) {
        apiObservers -= observer
    }

    private fun broadcastApiRequest(request: KlaviyoApiRequest) {
        apiObservers.forEach { it(request) }
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
            broadcastApiRequest(request)
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

        Registry.log.info("Restoring persisted queue")

        Registry.dataStore.fetch(QUEUE_KEY)?.let {
            try {
                val queue = JSONArray(it)
                Array(queue.length()) { i -> queue.optString(i) }
            } catch (exception: JSONException) {
                wasMutated = true
                Registry.log.wtf("Invalid persistent queue JSON", exception)
                Registry.log.debug(it)
                emptyArray<String>()
            }
        }?.forEach { uuid ->
            Registry.dataStore.fetch(uuid).let { json ->
                if (json == null) {
                    Registry.log.error("Missing request JSON for $uuid")
                    wasMutated = true
                } else {
                    try {
                        val request = KlaviyoApiRequest.fromJson(JSONObject(json))
                        if (!apiQueue.contains(request)) {
                            apiQueue.offer(request)
                        }
                    } catch (exception: JSONException) {
                        wasMutated = true
                        Registry.log.wtf("Invalid request JSON $uuid", exception)
                        Registry.dataStore.clear(uuid)
                        Registry.log.debug(json)
                    }
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
    private fun persistQueue() {
        Registry.log.info("Persisting queue")
        Registry.dataStore.store(
            QUEUE_KEY,
            JSONArray(apiQueue.map { it.uuid }).toString()
        )
    }

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
        Registry.log.info("Started background handler")
    }

    /**
     * Stop all jobs on our handler thread
     */
    private fun stopBatch() = handler?.removeCallbacksAndMessages(null).also {
        Registry.log.info("Stopped background handler")
    }

    /**
     * Runnable which flushes the API queue in batches for efficiency.
     * As long as there are items in the queue, the thread will loop.
     * When the queue is cleared the thread will not loop itself and will terminate.
     *
     * @property force Boolean that will force the queue to flush now
     */
    class NetworkRunnable(private var force: Boolean = false) : Runnable {
        private val queueInitTime = Registry.clock.currentTimeMillis()

        private var networkType: Int = Registry.networkMonitor.getNetworkType().position

        private var flushInterval: Long = Registry.config.networkFlushIntervals[networkType].toLong()

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

            Registry.log.info("Starting network batch")

            while (apiQueue.isNotEmpty()) {
                val request = apiQueue.poll()

                when (request?.send { broadcastApiRequest(request) }) {
                    Status.Complete, Status.Failed -> {
                        // On success or absolute failure, remove from queue and persistent store
                        Registry.dataStore.clear(request.uuid)
                        broadcastApiRequest(request)
                    }
                    Status.PendingRetry -> {
                        // Encountered a retryable error
                        // Put this back on top of the queue and we'll try again with backoff
                        // TODO reset flush interval next time succeeds
                        apiQueue.offerFirst(request)
                        flushInterval *= request.attempts + 1
                        broadcastApiRequest(request)
                        break
                    }
                    // Offline or at the end of the queue... either way break the loop
                    Status.Inflight, Status.Unsent, null -> break
                }
            }

            persistQueue()

            if (!apiQueue.isEmpty()) {
                requeue()
            } else {
                Registry.log.info("Emptied network queue")
            }
        }

        /**
         * Re-queue the job to run again after [flushInterval] milliseconds
         */
        private fun requeue() {
            Registry.log.info("Retrying network batch in $flushInterval")
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

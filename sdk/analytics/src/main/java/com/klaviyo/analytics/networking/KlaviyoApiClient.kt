package com.klaviyo.analytics.networking

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.requests.EventApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest.Status
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequestDecoder
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

    private var handlerThread = HandlerUtil.getHandlerThread(KlaviyoApiClient::class.simpleName)
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
                is ActivityEvent.AllStopped -> startBatch(true)
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

    override fun onApiRequest(withHistory: Boolean, observer: ApiObserver) {
        if (withHistory) {
            apiQueue.forEach(observer)
        }

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

        var addedRequest = false
        requests.forEach { request ->
            if (!apiQueue.contains(request)) {
                apiQueue.offer(request)
                Registry.dataStore.store(request.uuid, request.toString())
                broadcastApiRequest(request)
                addedRequest = true
            }
        }
        if (addedRequest) {
            persistQueue()
        }
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
    override fun restoreQueue() {
        apiQueue.clear()

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
                        val request = KlaviyoApiRequestDecoder.fromJson(JSONObject(json))
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
    override fun persistQueue() {
        Registry.log.info("Persisting queue")
        Registry.dataStore.store(
            QUEUE_KEY,
            JSONArray(apiQueue.map { it.uuid }).toString()
        )
    }

    /**
     * Start
     */
    override fun flushQueue() = startBatch(true)

    /**
     * Start a network batch to process the request queue
     *
     * This method is synchronized to avoid potentially starting the same thread twice.
     * Since we only ever have one thread running for our network requests, this is fine but if we ever extrapolate on this, we may want to revisit this logic
     * e.g: Synchronizing on the object instance (this) because I don't think we need to synchronize on anything else in this object. We may want to use a proper lock if we need more synchronized blocks or utilize more threading
     *
     * Furthermore, it should be noted that we check the thread state to ensure that the thread is not yet started (in new state) before trying to start it. This is more accurate than checking isAlive on the thread (https://stackoverflow.com/questions/58668916/thread-start-throwing-exception-after-thread-isalive-check)
     */
    private fun initBatch() {
        synchronized(this) {
            if (handlerThread.state == Thread.State.TERMINATED) {
                handlerThread = HandlerUtil.getHandlerThread(KlaviyoApiClient::class.simpleName)
            }

            if (handlerThread.state == Thread.State.NEW) {
                handlerThread.start()
                handler = HandlerUtil.getHandler(handlerThread.looper)
            }
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
    private fun stopBatch() {
        handler?.removeCallbacksAndMessages(null)
        Registry.log.info("Stopped background handler")
    }

    /**
     * Runnable which flushes the API queue in batches for efficiency.
     * As long as there are items in the queue, the thread will loop.
     * When the queue is cleared the thread will not loop itself and will terminate.
     *
     * @property force Boolean that will force the queue to flush now
     */
    internal class NetworkRunnable(force: Boolean = false) : Runnable {

        var force = force
            private set

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
                    Status.Unsent -> {
                        // Incomplete state: put it back on the queue and break out of serial queue
                        apiQueue.offerFirst(request)
                        break
                    }
                    Status.Complete, Status.Failed -> {
                        // On success or absolute failure, remove from queue and persistent store
                        Registry.dataStore.clear(request.uuid)
                        // Reset the flush interval, in case we had done any exp backoff
                        flushInterval = Registry.config.networkFlushIntervals[networkType].toLong()
                        broadcastApiRequest(request)
                    }
                    Status.PendingRetry -> {
                        // Encountered a retryable error
                        // Put this back on top of the queue and we'll try again with backoff
                        apiQueue.offerFirst(request)
                        flushInterval *= request.attempts + 1
                        broadcastApiRequest(request)
                        break
                    }
                    // These should not strictly be possible...
                    Status.Inflight -> Registry.log.wtf(
                        "Request state was not updated from Inflight"
                    )
                    null -> Registry.log.wtf("Queue contains an empty request")
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

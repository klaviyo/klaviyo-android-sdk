package com.klaviyo.analytics.networking

import android.os.Handler
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.requests.AggregateEventApiRequest
import com.klaviyo.analytics.networking.requests.AggregateEventPayload
import com.klaviyo.analytics.networking.requests.EventApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest.Status
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequestDecoder
import com.klaviyo.analytics.networking.requests.ProfileApiRequest
import com.klaviyo.analytics.networking.requests.PushTokenApiRequest
import com.klaviyo.analytics.networking.requests.UnregisterPushTokenApiRequest
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Coordinator of API request traffic
 */
internal object KlaviyoApiClient : ApiClient {
    internal const val QUEUE_KEY = "klaviyo_api_request_queue"

    private var handlerThread = Registry.threadHelper.getHandlerThread(
        KlaviyoApiClient::class.simpleName
    )
    private var handler: Handler? = null
    private var apiQueue = ConcurrentLinkedDeque<KlaviyoApiRequest>()
    private var queueInitialized = false

    /**
     * List of registered API observers
     */
    private val apiObservers = Collections.synchronizedList(
        CopyOnWriteArrayList<ApiObserver>()
    )

    /**
     * Initialize logic including lifecycle observers and reviving the queue from persistent store
     */
    override fun startService() {
        Registry.lifecycleMonitor.offActivityEvent(::onLifecycleActivity)
        Registry.lifecycleMonitor.onActivityEvent(::onLifecycleActivity)

        Registry.networkMonitor.offNetworkChange(::onNetworkChange)
        Registry.networkMonitor.onNetworkChange(::onNetworkChange)

        if (!queueInitialized) {
            // We only need to restore queue from persistent store once
            restoreQueue()
            queueInitialized = true
        }
    }

    override fun enqueueProfile(profile: Profile) {
        Registry.log.verbose("Enqueuing Profile request")
        enqueueRequest(ProfileApiRequest(profile))
    }

    override fun enqueuePushToken(token: String, profile: Profile) {
        Registry.log.verbose("Enqueuing Push Token request")
        enqueueRequest(PushTokenApiRequest(token, profile))
    }

    override fun enqueueAggregateEvent(payload: AggregateEventPayload) {
        Registry.log.verbose("Enqueuing Aggregate Event request")
        enqueueRequest(AggregateEventApiRequest(payload))
    }

    override fun enqueueUnregisterPushToken(apiKey: String, token: String, profile: Profile) {
        Registry.log.verbose("Enqueuing unregister token request")
        enqueueRequest(UnregisterPushTokenApiRequest(apiKey, token, profile))
    }

    override fun enqueueEvent(event: Event, profile: Profile) {
        Registry.log.verbose("Enqueuing ${event.metric.name} event")
        enqueueRequest(EventApiRequest(event, profile))

        if (event.metric == EventMetric.OPENED_PUSH) {
            flushQueue()
        }
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
                Registry.dataStore.store(request.uuid, request.toString())
                apiQueue.offer(request)
                broadcastApiRequest(request)
                addedRequest = true
            }
        }
        if (addedRequest) {
            persistQueue()
        }
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
        when (request.status) {
            Status.Unsent -> Registry.log.verbose("${request.type} Request enqueued")
            Status.Inflight -> Registry.log.verbose("${request.type} Request inflight")
            Status.PendingRetry -> {
                val attemptsRemaining = Registry.config.networkMaxAttempts - request.attempts
                Registry.log.warning(
                    "${request.type} Request failed with code ${request.responseCode}, and will be retried up to $attemptsRemaining more times."
                )
            }
            Status.Complete -> Registry.log.verbose(
                "${request.type} Request succeeded with code ${request.responseCode}"
            )
            else -> Registry.log.error(
                "${request.type} Request failed with code ${request.responseCode}, and will be dropped"
            )
        }

        request.responseBody?.let { response ->
            val body = request.requestBody?.let { JSONObject(it).toString(2) }
            Registry.log.verbose("${request.httpMethod}: ${request.url}")
            Registry.log.verbose("Headers: ${request.headers}")
            Registry.log.verbose("Query: ${request.query}")
            Registry.log.verbose("Body: $body")
            Registry.log.verbose("${request.responseCode} $response")
        }

        synchronized(apiObservers) {
            apiObservers.forEach { it(request) }
        }
    }

    /**
     * Stop our handler thread when all activities stop
     */
    private fun onLifecycleActivity(activity: ActivityEvent) = when (activity) {
        is ActivityEvent.AllStopped -> startBatch(true)
        else -> Unit
    }

    /**
     * Stop the background batching job while offline
     */
    private fun onNetworkChange(isConnected: Boolean) = if (isConnected) {
        startBatch(true)
    } else {
        stopBatch()
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

        Registry.dataStore.fetch(QUEUE_KEY)?.let {
            Registry.log.verbose("Restoring persisted queue")

            try {
                val queue = JSONArray(it)
                Array(queue.length()) { i -> queue.optString(i) }
            } catch (exception: JSONException) {
                wasMutated = true
                Registry.log.wtf("Invalid persistent queue JSON", exception)
                Registry.log.info(it)
                emptyArray<String>()
            }
        }?.forEach { uuid ->
            Registry.dataStore.fetch(uuid).let { json ->
                if (json == null) {
                    Registry.log.debug("Missing request JSON for $uuid")
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
                        Registry.log.info(json)
                        Registry.dataStore.clear(uuid)
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
        Registry.log.verbose("Persisting queue")
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
                handlerThread = Registry.threadHelper.getHandlerThread(
                    KlaviyoApiClient::class.simpleName
                )
            }

            if (handlerThread.state == Thread.State.NEW) {
                handlerThread.start()
                handler = Registry.threadHelper.getHandler(handlerThread.looper)
            }
        }

        startBatch()
    }

    /**
     * Start network runner job on the handler thread
     */
    private fun startBatch(force: Boolean = false) {
        stopBatch() // we only ever want one batch job running
        handler?.post(NetworkRunnable(force)).also {
            Registry.log.verbose("Posted job to network handler message queue")
        }
    }

    /**
     * Stop all jobs on our handler thread
     */
    private fun stopBatch() {
        handler?.removeCallbacksAndMessages(null).also {
            Registry.log.verbose("Cleared jobs from network handler message queue")
        }
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

        private var enqueuedTime = Registry.clock.currentTimeMillis()

        private var networkType: Int = Registry.networkMonitor.getNetworkType().position

        private var flushInterval: Long = Registry.config.networkFlushIntervals[networkType]

        private val flushDepth: Int = Registry.config.networkFlushDepth

        /**
         * Send queued requests serially
         * The queue will flush whenever the triggers specified in config are met
         * Posts another delayed batch job if requests remains
         */
        override fun run() {
            val queueTimePassed = Registry.clock.currentTimeMillis() - enqueuedTime

            if (getQueueSize() < flushDepth && queueTimePassed < flushInterval && !force) {
                return requeue()
            }

            Registry.log.verbose("Starting network batch")

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
                        flushInterval = Registry.config.networkFlushIntervals[networkType]
                        broadcastApiRequest(request)
                    }
                    Status.PendingRetry -> {
                        // Encountered a retryable error
                        // Put this back on top of the queue, and we'll try again with backoff
                        apiQueue.offerFirst(request)
                        flushInterval = request.computeRetryInterval()
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
                Registry.log.verbose("Emptied network queue")
            }
        }

        /**
         * Re-queue the job to run again after [flushInterval] milliseconds
         */
        private fun requeue() {
            Registry.log.verbose("Network batch will run in $flushInterval ms")
            force = false
            enqueuedTime = Registry.clock.currentTimeMillis()
            handler?.postDelayed(this, flushInterval)
        }
    }
}

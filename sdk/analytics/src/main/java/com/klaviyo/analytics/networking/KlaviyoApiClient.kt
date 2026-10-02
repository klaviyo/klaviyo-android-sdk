package com.klaviyo.analytics.networking

import android.os.Handler
import androidx.annotation.WorkerThread
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.Subscription
import com.klaviyo.analytics.networking.requests.AggregateEventApiRequest
import com.klaviyo.analytics.networking.requests.AggregateEventPayload
import com.klaviyo.analytics.networking.requests.ApiLane
import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.networking.requests.EventApiRequest
import com.klaviyo.analytics.networking.requests.FetchGeofencesCallback
import com.klaviyo.analytics.networking.requests.FetchGeofencesRequest
import com.klaviyo.analytics.networking.requests.FetchGeofencesResult
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest.Status
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequestDecoder
import com.klaviyo.analytics.networking.requests.ProfileApiRequest
import com.klaviyo.analytics.networking.requests.PushTokenApiRequest
import com.klaviyo.analytics.networking.requests.ResolveDestinationCallback
import com.klaviyo.analytics.networking.requests.ResolveDestinationResult
import com.klaviyo.analytics.networking.requests.SubscriptionApiRequest
import com.klaviyo.analytics.networking.requests.UniversalClickTrackRequest
import com.klaviyo.analytics.networking.requests.UnregisterPushTokenApiRequest
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.safeLaunch
import com.klaviyo.core.utils.takeIf
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Coordinator of API request traffic
 */
internal object KlaviyoApiClient : ApiClient {
    internal const val QUEUE_KEY = "klaviyo_api_request_queue"

    /**
     * Maximum number of requests that can sit in the API queue at one time.
     *
     * When the queue reaches this limit, the oldest request is evicted to make room.
     * We evict by enqueued time, not simply queue position, to honor actual age.
     * This prevents the queue from growing unbounded during request storms.
     */
    internal const val MAX_QUEUE_SIZE: Int = 200

    /**
     * Largest persisted queue whose entries are all examined when restoring.
     *
     * Beyond this, only a [MAX_QUEUE_SIZE] window from each end is read, so the cost of restoring
     * stays bounded no matter how large a backlog grew.
     */
    internal const val MAX_RESTORE_CANDIDATES: Int = MAX_QUEUE_SIZE * 2

    /**
     * Hard upper bound on requests in flight across all lanes at once.
     *
     * A lane can hold at most one request in flight, so the bound is reached only when
     * every lane is sending concurrently. It exists to cap total concurrent connections
     * no matter how many lanes are defined.
     */
    internal const val MAX_IN_FLIGHT: Int = 3

    private var handlerThread = Registry.threadHelper.getHandlerThread(
        KlaviyoApiClient::class.simpleName
    )
    private var handler: Handler? = null
    private var apiQueue = ConcurrentLinkedDeque<KlaviyoApiRequest>()
    private var queueInitialized = false

    /**
     * Mutable per-lane scheduling state. All fields are guarded by the enclosing
     * [KlaviyoApiClient] monitor via [withLaneLock]; nothing here is persisted.
     */
    private class LaneRuntime {
        /** The request from this lane currently being sent, if any. */
        var inFlightRequest: KlaviyoApiRequest? = null

        /** Whether a request from this lane is currently being sent. */
        val inFlight: Boolean get() = inFlightRequest != null

        /**
         * Earliest wall-clock time (per [com.klaviyo.core.Registry.clock]) at which this lane
         * may dispatch its next request. Advanced by per-request retry backoff.
         */
        var nextEligibleTime: Long = 0L
    }

    /** Runtime scheduling state per lane, created on demand. */
    private val laneRuntime = ConcurrentHashMap<ApiLane, LaneRuntime>()

    /**
     * Number of requests currently being sent across all lanes. Observable so
     * [awaitFlushQueueOutcome] can suspend until dispatched sends finish.
     */
    private val inFlightCount = MutableStateFlow(0)

    /** Round-robin cursor for fair selection among eligible lanes. */
    private var roundRobinCursor = 0

    /**
     * Executor that runs the blocking send for one lane's head request.
     *
     * Production uses a pool bounded to [MAX_IN_FLIGHT] workers so lanes genuinely send
     * concurrently. Tests substitute a synchronous runner to keep drains deterministic.
     */
    internal var laneSendExecutor: (Runnable) -> Unit = { runnable ->
        laneThreadPool.execute(runnable)
    }

    private val laneThreadPool by lazy {
        Executors.newFixedThreadPool(MAX_IN_FLIGHT)
    }

    /**
     * Run [block] holding the client-wide lane lock. Guards [laneRuntime] contents,
     * [roundRobinCursor], and the check-then-mark of lane in-flight state so two threads
     * never dispatch the same lane concurrently.
     */
    private inline fun <T> withLaneLock(block: () -> T): T = synchronized(laneRuntime, block)

    /**
     * Clear all runtime lane scheduling state. Lane state is never persisted, so this matches
     * a process restart. Intended for tests; production relies on process death to reset it.
     */
    internal fun resetLaneState() {
        withLaneLock {
            laneRuntime.clear()
            roundRobinCursor = 0
            inFlightCount.value = 0
        }
    }

    private val scheduler get() = Registry.getOrNull<QueueScheduler>()
        ?: WorkManagerQueueScheduler(Registry.config.applicationContext).also {
            Registry.register<QueueScheduler>(it)
        }

    /**
     * List of registered API observers
     */
    private val apiObservers = CopyOnWriteArrayList<ApiObserver>()

    /**
     * Initialize logic including lifecycle observers and reviving the queue from persistent store
     */
    override fun startService() {
        Registry.lifecycleMonitor.offActivityEvent(::onLifecycleActivity)
        Registry.lifecycleMonitor.onActivityEvent(::onLifecycleActivity)

        Registry.networkMonitor.offNetworkChange(::onNetworkChange)
        Registry.networkMonitor.onNetworkChange(::onNetworkChange)

        restoreQueue(forceRestore = false)

        if (apiQueue.isNotEmpty()) {
            initBatch()
        }
    }

    override fun enqueueProfile(profile: Profile): ApiRequest = ProfileApiRequest(profile).also {
        Registry.log.verbose("Enqueuing Profile request")
        enqueueRequest(it)
    }

    override fun enqueuePushToken(token: String, profile: Profile): ApiRequest =
        PushTokenApiRequest(token, profile).also {
            Registry.log.verbose("Enqueuing Push Token request")
            enqueueRequest(it)
        }

    override fun enqueueSubscription(subscription: Subscription, profile: Profile): ApiRequest? =
        SubscriptionApiRequest.from(subscription, profile)?.also {
            Registry.log.verbose("Enqueuing Subscription request")
            enqueueRequest(it)
        }

    override fun enqueueAggregateEvent(payload: AggregateEventPayload): ApiRequest =
        AggregateEventApiRequest(payload).also {
            Registry.log.verbose("Enqueuing Aggregate Event request")
            enqueueRequest(it)
        }

    override fun enqueueUnregisterPushToken(
        apiKey: String,
        token: String,
        profile: Profile
    ): ApiRequest =
        UnregisterPushTokenApiRequest(apiKey, token, profile).also {
            Registry.log.verbose("Enqueuing unregister token request")
            enqueueRequest(it)
        }

    override fun enqueueEvent(event: Event, profile: Profile): ApiRequest =
        EventApiRequest(event, profile).also { request ->
            Registry.log.verbose("Enqueuing ${event.metric.name} event")
            enqueueRequest(request, headOfLine = event.metric.isKlaviyoMetric)

            if (event.metric.isKlaviyoMetric) {
                // Use WorkManager to schedule flush for priority Klaviyo events
                // This ensures ASAP delivery even during doze mode, app standby etc.
                scheduler.scheduleFlush()
            }
        }

    /**
     * Resolve the destination URL for a universal click tracking link
     * or enqueue a retry to record a click later if it fails.
     */
    override suspend fun resolveDestinationUrl(
        trackingUrl: String,
        profile: Profile
    ): ResolveDestinationResult = withContext(Registry.dispatcher) {
        UniversalClickTrackRequest(
            trackingUrl,
            profile
        ).resolveOrEnqueue()
    }

    /**
     * Resolve the destination URL for a universal click tracking link
     * or enqueue a retry to record a click later if it fails.
     *
     * Note: callback-based implementation for Java interop, Kotlin devs are encouraged to use suspend implementation
     */
    override fun resolveDestinationUrl(
        trackingUrl: String,
        profile: Profile,
        callback: ResolveDestinationCallback
    ): ApiRequest = UniversalClickTrackRequest(trackingUrl, profile).apply {
        CoroutineScope(Registry.dispatcher).safeLaunch {
            callback(resolveOrEnqueue())
        }
    }

    /**
     * Blocking method to resolve the destination URL for a universal click tracking link
     * or enqueue a retry to record a click later if it fails.
     */
    private fun UniversalClickTrackRequest.resolveOrEnqueue(): ResolveDestinationResult {
        sendAndBroadcast()
        return getResult().also { result ->
            if (result is ResolveDestinationResult.Unavailable) {
                enqueueRequest(prepareToEnqueue())
            }
        }
    }

    /**
     * Fetch geofences from the Klaviyo API
     */
    override suspend fun fetchGeofences(
        latitude: Double?,
        longitude: Double?
    ): FetchGeofencesResult = withContext(Registry.dispatcher) {
        FetchGeofencesRequest(latitude, longitude).apply {
            sendAndBroadcast()
        }.getResult()
    }

    /**
     * Fetch geofences from the Klaviyo API
     *
     * Note: callback-based implementation for Java interop, Kotlin devs are encouraged to use suspend implementation
     */
    override fun fetchGeofences(
        latitude: Double?,
        longitude: Double?,
        callback: FetchGeofencesCallback
    ): ApiRequest = FetchGeofencesRequest(latitude, longitude).apply {
        CoroutineScope(Registry.dispatcher).safeLaunch {
            sendAndBroadcast()
            callback(getResult())
        }
    }

    /**
     * Enqueues one or more [KlaviyoApiRequest]s to send on a background thread
     * These requests are sent to the Klaviyo asynchronous APIs
     *
     * This method will initialize the API queue and the batching thread
     * if this is the first request made since launch.
     */
    fun enqueueRequest(vararg requests: KlaviyoApiRequest, headOfLine: Boolean = false) {
        if (apiQueue.isEmpty()) {
            initBatch()
        }

        var addedRequest = false
        requests.let {
            // Reverse the arg order if headOfLine is true, so that first arg winds up first in line
            if (headOfLine) {
                requests.reversed()
            } else {
                requests.asList()
            }
        }.forEach { request ->
            if (!apiQueue.contains(request)) {
                Registry.dataStore.store(request.uuid, request.toString())
                if (headOfLine) {
                    apiQueue.offerFirst(request)
                } else {
                    apiQueue.offer(request)
                }
                broadcastApiRequest(request)
                addedRequest = true
            }
        }

        val trimmed = trimToCapacity()

        if (addedRequest || trimmed) {
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
                val attemptsRemaining = request.maxAttempts - request.attempts
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

        apiObservers.forEach { it(request) }
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
     *
     * @param forceRestore If true, always restore from persistent store.
     *                     If false, only restore if not already initialized.
     */
    override fun restoreQueue(forceRestore: Boolean) {
        if (!forceRestore && queueInitialized) {
            return
        }

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
        }?.let { uuids ->
            selectNewestWithinCapacity(uuids).also {
                if (it.size < uuids.size) wasMutated = true
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
        if (wasMutated) {
            persistQueue()
        }

        queueInitialized = true
    }

    /**
     * Narrow a persisted queue to the entries worth examining, so the cost of restoring is bounded
     * by [MAX_QUEUE_SIZE] rather than by how large the backlog grew.
     *
     * Reading a request's timestamp costs a JSON parse, so examining every entry of an unbounded
     * backlog scales without limit — a queue an order of magnitude beyond capacity would spend
     * seconds on it before the SDK finishes starting up.
     *
     * Entries are taken from both ends because each holds requests worth keeping: priority
     * requests are inserted at the front of the deque, and the most recently enqueued are appended
     * at the back. Anything in between is older than a full queue's worth of requests on both
     * sides, and is discarded without being read.
     */
    private fun boundCandidates(uuids: List<String>): List<String> {
        Registry.log.warning(
            "Persisted queue of ${uuids.size} exceeds capacity ($MAX_QUEUE_SIZE), " +
                "discarding the most outdated requests"
        )

        if (uuids.size <= MAX_RESTORE_CANDIDATES) return uuids

        return uuids.take(MAX_QUEUE_SIZE) + uuids.takeLast(MAX_QUEUE_SIZE)
    }

    /**
     * Narrow a persisted uuid list to the newest [MAX_QUEUE_SIZE] entries by enqueue timestamp,
     * clearing the rest from the persistent store.
     *
     * A store written by an SDK version that predates the queue cap can hold an unbounded number
     * of requests. Selecting before decoding keeps the discarded requests from being decoded into
     * [KlaviyoApiRequest] objects: only each entry's timestamp is read here, and the JSON is
     * released before the next entry is examined. The raw bodies are resident in the
     * SharedPreferences map for the process lifetime regardless, so this bounds the decoded
     * request objects, not the store's own footprint.
     *
     * Entries whose timestamp cannot be read sort as oldest, so they are discarded first.
     *
     * @return the uuids to restore, in their original order
     */
    private fun selectNewestWithinCapacity(persisted: Array<String>): Array<String> {
        // A malformed index can repeat a uuid, and retaining every occurrence of one would both
        // exceed the cap and re-decode the same request. Deduplicating also marks the index as
        // mutated, so the normalized form replaces it on disk.
        val uuids = persisted.distinct()
        if (uuids.size <= MAX_QUEUE_SIZE) return uuids.toTypedArray()

        val candidates = boundCandidates(uuids)

        // Read each timestamp exactly once: a sort selector is re-invoked per comparison, which
        // would re-parse every request body O(n log n) times.
        val queuedTimes = candidates.associateWith { uuid ->
            Registry.dataStore.fetch(uuid)?.let { json ->
                try {
                    JSONObject(json).optLong(KlaviyoApiRequest.TIME_JSON_KEY, Long.MIN_VALUE)
                } catch (exception: JSONException) {
                    Registry.log.debug("Invalid request JSON $uuid", exception)
                    Long.MIN_VALUE
                }
            } ?: Long.MIN_VALUE
        }

        val retained = candidates.sortedByDescending { queuedTimes[it] }
            .take(MAX_QUEUE_SIZE)
            .toSet()

        Registry.dataStore.clear(uuids.filterNot(retained::contains))

        return uuids.filter(retained::contains).toTypedArray()
    }

    /**
     * Drop the oldest requests by [KlaviyoApiRequest.queuedTime] until the queue is within
     * [MAX_QUEUE_SIZE], removing them from the persistent store in a single write.
     *
     * Requests are evicted by enqueue timestamp rather than deque position, because head-of-line
     * requests are inserted at the front but are the newest.
     *
     * Selecting victims and removing them is not atomic on the [ConcurrentLinkedDeque], so a
     * concurrent enqueue could target the same victim. Side effects are gated on `remove()`'s
     * boolean: a request another thread already removed is not reported or cleared, making this a
     * best-effort soft bound rather than a hard guarantee. Requests currently in flight are never
     * evicted.
     *
     * @return whether any requests were dropped
     */
    private fun trimToCapacity(): Boolean {
        val overflow = apiQueue.size - MAX_QUEUE_SIZE
        if (overflow <= 0) return false

        // Requests mid-send stay queued until their lane completes, so never evict them;
        // the cap may be briefly exceeded by at most MAX_IN_FLIGHT entries.
        val inFlight = withLaneLock { laneRuntime.values.mapNotNullTo(HashSet()) { it.inFlightRequest } }

        val evictedUuids = apiQueue.sortedBy { it.queuedTime }
            .filterNot(inFlight::contains)
            .take(overflow)
            .filter { apiQueue.remove(it) }
            .map { request ->
                Registry.log.warning(
                    "API queue at capacity ($MAX_QUEUE_SIZE), evicting oldest request: ${request.type}"
                )
                request.uuid
            }

        Registry.dataStore.clear(evictedUuids)

        return evictedUuids.isNotEmpty()
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
     * Tell the client to attempt to flush network request queue now
     */
    override fun flushQueue() {
        startBatch(true)
    }

    /**
     * Flushes the queue in a background context, suspending until every dispatched lane send
     * has finished so callers (e.g. a WorkManager job) don't release their hold mid-send.
     *
     * Each time in-flight sends settle, lanes are drained again to dispatch work that became
     * eligible; the loop ends once a drain leaves nothing in flight.
     *
     * @returns [FlushOutcome] indicating whether requests remain in the queue
     */
    override suspend fun awaitFlushQueueOutcome() = withContext(Registry.dispatcher) {
        var outcome = drainEligibleLanes()
        while (inFlightCount.value > 0) {
            inFlightCount.first { it == 0 }
            outcome = drainEligibleLanes()
        }
        outcome
    }

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
     * The lane a request belongs to, derived from its endpoint at scheduling time.
     * Recomputed on every use so nothing lane-related is persisted.
     */
    private fun laneOf(request: KlaviyoApiRequest): ApiLane = ApiLane.fromRequest(request)

    /**
     * Snapshot the queue into FIFO-ordered requests per lane, preserving head-of-line
     * priority within the events lane (priority requests sit at the front of the deque,
     * so deque order already reflects within-lane FIFO with head-of-line first).
     */
    private fun queueByLane(): Map<ApiLane, List<KlaviyoApiRequest>> {
        val byLane = linkedMapOf<ApiLane, MutableList<KlaviyoApiRequest>>()
        apiQueue.forEach { request ->
            byLane.getOrPut(laneOf(request)) { mutableListOf() }.add(request)
        }
        return byLane
    }

    /**
     * Drain every currently-eligible lane once, dispatching each lane's head request for
     * sending subject to the per-lane one-in-flight rule and the [MAX_IN_FLIGHT] global bound.
     *
     * A lane is eligible when it has queued work, is not already in flight, and the current
     * time has reached its [LaneRuntime.nextEligibleTime]. Eligible lanes are visited in
     * round-robin order from [roundRobinCursor] so no lane starves another.
     *
     * With a synchronous [laneSendExecutor] (tests) each send completes inline and the loop
     * keeps draining; with the async pool (production) completion re-enters [pumpLanes] via
     * [onLaneSendComplete] to dispatch work that became eligible.
     *
     * Note: this must be run from a background thread/context.
     *
     * @return [FlushOutcome.Complete] when the queue emptied, else [FlushOutcome.Incomplete]
     *         carrying the soonest wake-up delay across lanes that still have work.
     */
    @WorkerThread
    private fun drainEligibleLanes(): FlushOutcome {
        Registry.log.verbose("Starting network batch")

        val now = Registry.clock.currentTimeMillis()

        // Loop because a synchronous executor drains further lanes as earlier ones complete.
        // Each pass either dispatches at least one send or finds nothing eligible and exits.
        while (true) {
            val dispatched = withLaneLock {
                if (inFlightCount.value >= MAX_IN_FLIGHT) {
                    false
                } else {
                    nextDispatchCandidate(now)?.let { (lane, request) ->
                        laneRuntime.getOrPut(lane) { LaneRuntime() }.inFlightRequest = request
                        inFlightCount.update { it + 1 }
                        dispatchLaneSend(lane, request)
                        true
                    } ?: false
                }
            }

            if (!dispatched) break
        }

        persistQueue()

        return if (apiQueue.isEmpty()) {
            Registry.log.verbose("Emptied network queue")
            FlushOutcome.Complete
        } else {
            val retryAfter = computeNextWakeDelay(now)
            Registry.log.verbose("Incomplete send: ${apiQueue.size} requests remain")
            FlushOutcome.Incomplete(retryAfter)
        }
    }

    /**
     * Pick the next (lane, head request) to dispatch, scanning lanes in round-robin order
     * from [roundRobinCursor] and advancing the cursor past the chosen lane.
     *
     * Caller must hold the lane lock.
     *
     * @return the chosen lane and its head request, or null if no lane is dispatchable now
     */
    private fun nextDispatchCandidate(now: Long): Pair<ApiLane, KlaviyoApiRequest>? {
        val byLane = queueByLane()
        if (byLane.isEmpty()) return null

        val lanes = byLane.keys.toList()
        // Visit lanes starting just after the cursor so selection rotates fairly.
        repeat(lanes.size) { offset ->
            val index = (roundRobinCursor + offset) % lanes.size
            val lane = lanes[index]
            val runtime = laneRuntime[lane]

            val inFlight = runtime?.inFlight == true
            val eligible = now >= (runtime?.nextEligibleTime ?: 0L)

            if (!inFlight && eligible) {
                val head = byLane[lane]?.firstOrNull()
                if (head != null) {
                    roundRobinCursor = (index + 1) % lanes.size
                    return lane to head
                }
            }
        }
        return null
    }

    /**
     * Hand a lane's head request to the [laneSendExecutor]. The request stays in the queue
     * until its send completes, so a process death mid-flight restores it on next launch.
     *
     * An unexpected throw from the send is treated as [Status.Unsent] so the lane's in-flight
     * slot is always released and the request is retried after the default flush interval.
     */
    private fun dispatchLaneSend(lane: ApiLane, request: KlaviyoApiRequest) {
        laneSendExecutor {
            val status = try {
                request.sendAndBroadcast()
            } catch (exception: Exception) {
                Registry.log.error("Unexpected error sending ${request.type}", exception)
                Status.Unsent
            }
            onLaneSendComplete(lane, request, status)
        }
    }

    /**
     * Reconcile a finished lane send: clear the lane's in-flight slot, apply per-lane
     * backoff on retry, remove completed/failed requests from the queue and store, then
     * dispatch any work that became eligible and schedule the next wake-up.
     */
    private fun onLaneSendComplete(
        lane: ApiLane,
        request: KlaviyoApiRequest,
        status: Status
    ) {
        when (status) {
            Status.Unsent -> {
                // Network dropped mid-send: leave the request queued and retry on the
                // default flush interval rather than hot-looping.
                withLaneLock {
                    laneRuntime.getOrPut(lane) { LaneRuntime() }.apply {
                        inFlightRequest = null
                        nextEligibleTime = Registry.clock.currentTimeMillis() + defaultFlushInterval
                    }
                    inFlightCount.update { it - 1 }
                }
            }

            Status.Complete, Status.Failed -> {
                // Terminal: remove from queue and persistent store exactly once.
                apiQueue.remove(request)
                Registry.dataStore.clear(request.uuid)
                withLaneLock {
                    laneRuntime.getOrPut(lane) { LaneRuntime() }.inFlightRequest = null
                    inFlightCount.update { it - 1 }
                }
            }

            Status.PendingRetry -> {
                // Retryable failure: keep the request at the head of its lane and hold the
                // lane until its backoff elapses. Other lanes are unaffected.
                val retryAfter = request.computeRetryInterval()
                withLaneLock {
                    laneRuntime.getOrPut(lane) { LaneRuntime() }.apply {
                        inFlightRequest = null
                        nextEligibleTime = Registry.clock.currentTimeMillis() + retryAfter
                    }
                    inFlightCount.update { it - 1 }
                }
            }

            // This should not be possible; release the lane slot so a stuck state can
            // never permanently wedge the lane or the global in-flight bound.
            Status.Inflight -> {
                Registry.log.wtf("Request state was not updated from Inflight")
                withLaneLock {
                    laneRuntime.getOrPut(lane) { LaneRuntime() }.inFlightRequest = null
                    inFlightCount.update { it - 1 }
                }
            }
        }

        persistQueue()
        pumpLanes()
    }

    /**
     * Dispatch any newly-eligible lane work and ensure a wake-up is scheduled for the
     * soonest lane that still has work but is backing off. Safe to call from any thread.
     */
    private fun pumpLanes() {
        if (apiQueue.isEmpty()) return

        // Wake immediately to dispatch lanes that became eligible. Forcing here bypasses
        // only the batching flush-interval gate; per-lane backoff (nextEligibleTime) is
        // still enforced inside drainEligibleLanes, so a backing-off lane is not disturbed.
        startBatch(force = true)
    }

    /**
     * Compute the delay until the next lane with queued work becomes eligible.
     *
     * @return the minimum per-lane wait, or null if a lane is eligible immediately
     */
    private fun computeNextWakeDelay(now: Long): Long? = withLaneLock {
        var soonest: Long? = null
        val byLane = queueByLane()

        byLane.forEach { (lane, requests) ->
            if (requests.isNotEmpty()) {
                val runtime = laneRuntime[lane]
                val eligibleAt = runtime?.nextEligibleTime ?: 0L
                val wait = eligibleAt - now
                if (wait <= 0L) {
                    // A lane is eligible right now; no need to defer.
                    return@withLaneLock null
                }
                val current = soonest
                if (current == null || wait < current) {
                    soonest = wait
                }
            }
        }
        soonest
    }

    private val currentNetworkType get() = Registry.networkMonitor.getNetworkType().position

    internal val defaultFlushInterval get() = Registry.config.networkFlushIntervals[currentNetworkType]

    /**
     * Runnable which flushes the API queue in batches for efficiency.
     * As long as there are items in the queue, the thread will loop and send serially.
     * When the queue is cleared the thread will not loop itself and will terminate.
     *
     * @property force Boolean that will force the queue to flush now
     */
    internal class NetworkRunnable(force: Boolean = false) : Runnable {

        var force = force
            private set

        private var enqueuedTime = Registry.clock.currentTimeMillis()

        private var flushInterval: Long = defaultFlushInterval

        /**
         * Send queued requests, one per eligible lane
         * The queue will flush whenever the triggers specified in config are met
         * Posts another delayed batch job if requests remains
         */
        override fun run() {
            val queueTimePassed = Registry.clock.currentTimeMillis() - enqueuedTime

            if (queueTimePassed < flushInterval && !force) {
                return requeue()
            }

            val outcome = drainEligibleLanes()

            outcome.takeIf<FlushOutcome.Incomplete>()?.retryAfter?.let { retryAfter ->
                flushInterval = retryAfter
            }

            if (!apiQueue.isEmpty()) {
                requeue()
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

    /**
     * Blocking method to send an API request, notifying API observers on transition between states
     */
    private fun KlaviyoApiRequest.sendAndBroadcast(): Status = send {
        broadcastApiRequest(this)
    }
}

package com.klaviyo.analytics.networking

import android.net.Uri
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.requests.AggregateEventPayload
import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.networking.requests.EventApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequestDecoder
import com.klaviyo.analytics.networking.requests.RequestMethod
import com.klaviyo.analytics.networking.requests.ResolveDestinationResult
import com.klaviyo.analytics.networking.requests.UniversalClickTrackRequest
import com.klaviyo.analytics.networking.requests.buildEventMetaData
import com.klaviyo.analytics.networking.requests.buildMetaData
import com.klaviyo.core.DeviceProperties
import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.core.networking.NetworkMonitor
import com.klaviyo.core.networking.NetworkObserver
import com.klaviyo.core.safeCall
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import com.klaviyo.fixtures.unmockDeviceProperties
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class KlaviyoApiClientTest : BaseTest() {

    private val flushIntervalWifi = 10_000L
    private val flushIntervalCell = 20_000L
    private val flushIntervalOffline = 30_000L
    private val queueDepth = 10
    private var postedJob: KlaviyoApiClient.NetworkRunnable? = null

    private companion object {
        private val slotOnActivityEvent = slot<ActivityObserver>()
        private val slotOnNetworkChange = slot<NetworkObserver>()
    }

    @Before
    override fun setup() {
        super.setup()

        // Set the Main dispatcher to use the test dispatcher
        Dispatchers.setMain(dispatcher)

        mockDeviceProperties()
        mockkStatic(DeviceProperties::buildEventMetaData)
        every { DeviceProperties.buildEventMetaData() } returns emptyMap()
        every { DeviceProperties.buildMetaData() } returns emptyMap()

        postedJob = null

        every { mockConfig.networkFlushIntervals } returns longArrayOf(
            flushIntervalWifi,
            flushIntervalCell,
            flushIntervalOffline
        )
        every { mockConfig.networkFlushDepth } returns queueDepth
        every { mockNetworkMonitor.isNetworkConnected() } returns false
        every { mockNetworkMonitor.getNetworkType() } returns NetworkMonitor.NetworkType.Wifi
        every { mockLifecycleMonitor.onActivityEvent(capture(slotOnActivityEvent)) } returns Unit
        every { mockLifecycleMonitor.offActivityEvent(capture(slotOnActivityEvent)) } returns Unit
        every { mockNetworkMonitor.onNetworkChange(capture(slotOnNetworkChange)) } returns Unit
        every { mockNetworkMonitor.offNetworkChange(capture(slotOnNetworkChange)) } returns Unit

        every { mockHandler.postDelayed(any(), any()) } answers {
            postedJob = firstArg<KlaviyoApiClient.NetworkRunnable>()
            true
        }

        KlaviyoApiClient.startService()
    }

    @After
    override fun cleanup() {
        // Reset the Main dispatcher
        Dispatchers.resetMain()

        spyDataStore.clear(KlaviyoApiClient.QUEUE_KEY)
        KlaviyoApiClient.restoreQueue()
        assertEquals(0, KlaviyoApiClient.getQueueSize())
        super.cleanup()
        unmockkObject(KlaviyoApiClient)
        unmockkObject(KlaviyoApiRequestDecoder)
        unmockDeviceProperties()
        unmockkStatic(DeviceProperties::buildEventMetaData)
    }

    private fun mockRequest(
        uuid: String = "uuid",
        status: KlaviyoApiRequest.Status = KlaviyoApiRequest.Status.Complete,
        codeOverride: Int? = null
    ): KlaviyoApiRequest =
        spyk(KlaviyoApiRequest("https://mock.com", RequestMethod.GET)).also {
            every { it.status } returns status
            every { it.state } returns status.name
            val getState = {
                when (it.state) {
                    KlaviyoApiRequest.Status.Unsent.name -> KlaviyoApiRequest.Status.Unsent
                    KlaviyoApiRequest.Status.Inflight.name -> KlaviyoApiRequest.Status.Inflight
                    KlaviyoApiRequest.Status.PendingRetry.name -> KlaviyoApiRequest.Status.PendingRetry
                    KlaviyoApiRequest.Status.Complete.name -> KlaviyoApiRequest.Status.Complete
                    KlaviyoApiRequest.Status.Failed.name -> KlaviyoApiRequest.Status.Failed
                    else -> error("Invalid state")
                }
            }

            // Initial attempts value
            var attempts = if (getState() == KlaviyoApiRequest.Status.Unsent) 0 else 1

            every { it.uuid } returns uuid
            every { it.type } returns "Mock"
            every { it.httpMethod } returns "GET"
            every { it.url } returns URL("https://mock.com")
            every { it.headers } returns mutableMapOf("headerKey" to "headerValue")
            every { it.query } returns mapOf("queryKey" to "queryValue")
            every { it.send(any()) } answers {
                attempts++
                getState()
            }
            every { it.responseHeaders } returns emptyMap()
            every { it.responseBody } returns null
            every { it.responseCode } answers {
                codeOverride ?: when (getState()) {
                    KlaviyoApiRequest.Status.PendingRetry -> 429
                    KlaviyoApiRequest.Status.Complete -> 202
                    KlaviyoApiRequest.Status.Failed -> 500
                    else -> null
                }
            }
            every { it.attempts } answers { attempts }
            every { it.toJson() } returns JSONObject(
                """
                {
                  "headers": {
                    "headerKey": "headerValue"
                  },
                  "method": "GET",
                  "query": {
                    "queryKey": "queryValue"
                  },
                  "time": "time",
                  "uuid": "$uuid",
                  "url_path": "test"
                }
            """
            )
            every { it.toString() } returns it.toJson().toString()
            every { it.equals(any()) } answers { a ->
                it.uuid == (a.invocation.args[0] as? KlaviyoApiRequest)?.uuid
            }
        }

    @Test
    fun `Is registered service`() {
        // An odd case, since KlaviyoApiClient is very reliant on other services just to init,
        // I am simply testing that Registry will return my object mock
        mockkObject(KlaviyoApiClient)
        unmockkObject(Registry)
        setup() // Also have to reset the test conditions to that cleanup can run properly
    }

    @Test
    fun `Enqueues a profile API call`() {
        assertEquals(0, KlaviyoApiClient.getQueueSize())

        KlaviyoApiClient.enqueueProfile(Profile().setAnonymousId(ANON_ID))

        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assertEquals(false, postedJob?.force)
    }

    @Test
    fun `Enqueues a push token API call`() {
        assertEquals(0, KlaviyoApiClient.getQueueSize())

        KlaviyoApiClient.enqueuePushToken(
            PUSH_TOKEN,
            Profile().setAnonymousId(ANON_ID)
        )

        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assertEquals(false, postedJob?.force)
    }

    @Test
    fun `Enqueues an unregister push API call`() {
        assertEquals(0, KlaviyoApiClient.getQueueSize())

        KlaviyoApiClient.enqueueUnregisterPushToken(
            "apiKey",
            PUSH_TOKEN,
            Profile().setAnonymousId(ANON_ID)
        )

        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assertEquals(false, postedJob?.force)
    }

    @Test
    fun `Enqueues a aggregate event API call`() {
        assertEquals(0, KlaviyoApiClient.getQueueSize())

        KlaviyoApiClient.enqueueAggregateEvent(AggregateEventPayload("{}"))

        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assertEquals(false, postedJob?.force)
    }

    @Test
    fun `Enqueuing the same push token API call multiple times only queues the first`() {
        assertEquals(0, KlaviyoApiClient.getQueueSize())

        repeat(5) {
            KlaviyoApiClient.enqueuePushToken(
                PUSH_TOKEN,
                Profile().setAnonymousId(ANON_ID)
            )
        }

        assertEquals(1, KlaviyoApiClient.getQueueSize())
        verify(exactly = 1) { spyLog.verbose("Persisting queue") }
    }

    @Test
    fun `Enqueues an event API call`() {
        mockkConstructor(EventApiRequest::class)
        assertEquals(0, KlaviyoApiClient.getQueueSize())

        KlaviyoApiClient.enqueueEvent(
            Event(EventMetric.CUSTOM("mock")),
            Profile().setAnonymousId(ANON_ID)
        )

        verify(inverse = true) { anyConstructed<EventApiRequest>().send(any()) }
        assertEquals(1, KlaviyoApiClient.getQueueSize())
        unmockkConstructor(EventApiRequest::class)
    }

    @Test
    fun `Enqueueing an Opened Push event flushes the queue immediately`() {
        mockkConstructor(EventApiRequest::class)
        every { anyConstructed<EventApiRequest>().send(any()) } returns KlaviyoApiRequest.Status.Complete

        KlaviyoApiClient.enqueueEvent(
            Event(EventMetric.OPENED_PUSH),
            Profile().setAnonymousId(ANON_ID)
        )

        verify { anyConstructed<EventApiRequest>().send(any()) }
        assertEquals(0, KlaviyoApiClient.getQueueSize())
        unmockkConstructor(EventApiRequest::class)
    }

    @Test
    fun `Supports idempotent re-starting`() {
        KlaviyoApiClient.startService()
        val priorOnActivityEvent = slotOnActivityEvent.captured
        val priorOnNetworkChange = slotOnNetworkChange.captured

        KlaviyoApiClient.enqueueRequest(mockRequest("abc123"))
        assertEquals(1, KlaviyoApiClient.getQueueSize())

        KlaviyoApiClient.startService()

        // Queue should be left as it was
        assertEquals(1, KlaviyoApiClient.getQueueSize())

        // Listeners should have been removed
        verify { mockLifecycleMonitor.offActivityEvent(priorOnActivityEvent) }
        verify { mockNetworkMonitor.offNetworkChange(priorOnNetworkChange) }
    }

    @Test
    fun `Supports adding and removing callbacks`() {
        var counter = 0
        val handler: ApiObserver = { counter++ }

        KlaviyoApiClient.onApiRequest(observer = handler)
        KlaviyoApiClient.enqueueProfile(Profile().setAnonymousId(ANON_ID))

        assertEquals(1, counter)

        KlaviyoApiClient.offApiRequest(handler)
        KlaviyoApiClient.enqueueProfile(Profile().setAnonymousId(ANON_ID))

        assertEquals(1, counter)
    }

    @Test
    fun `Invokes callback with existing queue if requested`() {
        var cbRequest: ApiRequest? = null
        val request = mockRequest(status = KlaviyoApiRequest.Status.Unsent)
        KlaviyoApiClient.enqueueRequest(request)

        KlaviyoApiClient.onApiRequest(true) { cbRequest = it }

        assertEquals(request, cbRequest)
        verify { spyLog.verbose(match { it.contains("queue") }) }
    }

    @Test
    fun `Invokes callback and logs when request enqueued`() {
        var cbRequest: ApiRequest? = null
        KlaviyoApiClient.onApiRequest { cbRequest = it }

        val request = mockRequest(status = KlaviyoApiRequest.Status.Unsent)
        KlaviyoApiClient.enqueueRequest(request)
        assertEquals(request, cbRequest)
        verify { spyLog.verbose(match { it.contains("queue") }) }
    }

    @Test
    fun `Concurrent modification exception does not get thrown on observers`() = runTest {
        val apiObserver: ApiObserver = { Thread.sleep(6) }
        val request = mockRequest()

        KlaviyoApiClient.onApiRequest(true, apiObserver)

        val job = launch(Dispatchers.IO) {
            KlaviyoApiClient.enqueueRequest(request)
        }
        val job2 = launch(Dispatchers.Default) {
            withContext(Dispatchers.IO) {
                Thread.sleep(8)
            }
            KlaviyoApiClient.offApiRequest(apiObserver)
        }
        job.start()
        job2.start()
    }

    @Test
    fun `Invokes callback and logs when request sent`() {
        every { mockConfig.networkFlushDepth } returns 1
        val request = mockRequest()
        KlaviyoApiClient.enqueueRequest(request)

        var cbRequest: ApiRequest? = null
        KlaviyoApiClient.onApiRequest { cbRequest = it }

        postedJob!!.run()
        assertEquals(request, cbRequest)
        verify { spyLog.verbose(match { it.contains("succeed") }) }
    }

    @Test
    fun `Flushes queue immediately on all stopped`() {
        var callCount = 0
        KlaviyoApiClient.enqueueRequest(mockRequest())
        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assert(slotOnActivityEvent.isCaptured)
        every { mockHandler.post(match { it is KlaviyoApiClient.NetworkRunnable && it.force }) } answers {
            callCount++ > 0
        }

        slotOnActivityEvent.captured(ActivityEvent.AllStopped())

        assertEquals(1, callCount)
    }

    @Test
    fun `Flushes queue on network restored`() {
        KlaviyoApiClient.enqueueRequest(mockRequest())
        staticClock.time += flushIntervalWifi
        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assert(slotOnNetworkChange.isCaptured)
        slotOnNetworkChange.captured(false)
        assertEquals(1, KlaviyoApiClient.getQueueSize())
        slotOnNetworkChange.captured(true)
        assertEquals(0, KlaviyoApiClient.getQueueSize())
    }

    @Test
    fun `API queuing accepts multiple requests per call`() {
        val requests = (0..5).map {
            mockRequest("$it-uuid")
        }

        KlaviyoApiClient.enqueueRequest(*requests.toTypedArray())
        assertEquals(requests.size, KlaviyoApiClient.getQueueSize())
    }

    @Test
    fun `Flushes queue when configured size is reached`() {
        repeat(queueDepth) {
            KlaviyoApiClient.enqueueRequest(mockRequest("uuid-$it"))
            assertEquals(it + 1, KlaviyoApiClient.getQueueSize())
        }

        postedJob!!.run()

        assertEquals(0, KlaviyoApiClient.getQueueSize())
    }

    @Test
    fun `Flushes queue when configured time has elapsed`() {
        val requestMock = mockRequest()

        KlaviyoApiClient.enqueueRequest(requestMock)

        staticClock.execute(flushIntervalWifi.toLong())

        postedJob!!.run()

        assertEquals(0, KlaviyoApiClient.getQueueSize())
    }

    @Test
    fun `Does not flush queue if no criteria is met`() {
        repeat(queueDepth - 1) {
            KlaviyoApiClient.enqueueRequest(mockRequest("uuid-$it"))
            assertEquals(it + 1, KlaviyoApiClient.getQueueSize())
        }

        postedJob!!.run()

        assertEquals(queueDepth - 1, KlaviyoApiClient.getQueueSize())
    }

    @Test
    fun `Flushes queue if forced when no criteria is met`() {
        repeat(queueDepth - 1) {
            KlaviyoApiClient.enqueueRequest(mockRequest("uuid-$it"))
            assertEquals(it + 1, KlaviyoApiClient.getQueueSize())
        }

        KlaviyoApiClient.flushQueue()

        assertEquals(0, KlaviyoApiClient.getQueueSize())
    }

    @Test
    fun `Failed requests are cleared from the queue`() {
        val fail = "uuid-failed"
        KlaviyoApiClient.enqueueRequest(mockRequest(fail, KlaviyoApiRequest.Status.Failed))

        KlaviyoApiClient.flushQueue()

        assertEquals(0, KlaviyoApiClient.getQueueSize())
        assertNull(spyDataStore.fetch(fail))
    }

    @Test
    fun `An unsent request is not removed from the queue`() {
        val uuid = "uuid-failed"
        KlaviyoApiClient.enqueueRequest(mockRequest(uuid, KlaviyoApiRequest.Status.Unsent))

        assertEquals(1, KlaviyoApiClient.getQueueSize())
        KlaviyoApiClient.flushQueue()

        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assertNotNull(spyDataStore.fetch(uuid))
    }

    @Test
    fun `Rate limited requests are retried according Retry-After header if present`() {
        // First unsent request, which we will retry till max attempts
        val request1 = mockRequest("uuid-retry", KlaviyoApiRequest.Status.Unsent).also {
            every { it.responseHeaders } returns mapOf("Retry-After" to listOf("50"))
        }

        every { request1.state } answers {
            when (request1.attempts) {
                0 -> KlaviyoApiRequest.Status.Unsent.name
                50 -> KlaviyoApiRequest.Status.Failed.name
                else -> KlaviyoApiRequest.Status.PendingRetry.name
            }
        }

        // Second unset request in queue to ensure which shouldn't sent until first has failed
        val request2 = mockRequest("uuid-unsent", KlaviyoApiRequest.Status.Unsent)

        // Enqueue 2 requests
        KlaviyoApiClient.enqueueRequest(request1, request2)

        // Enqueueing should invoke handler.post and initialize our postedJob property
        assertNotNull(postedJob)

        // But the clock has not advanced, so no requests should have been sent yet
        assertEquals(0, request1.attempts)

        while (request1.state != KlaviyoApiRequest.Status.Failed.name) {
            val startAttempts = request1.attempts

            // Advance the time with our expected backoff interval
            staticClock.time += 50_000L

            // Run after advancing the clock (this mimics how handler.postDelay would run jobs)
            postedJob!!.run()

            // It should have attempted one send if the correct time elapsed
            assertEquals(startAttempts + 1, request1.attempts)

            // Fail test if we exceed max attempts
            assert(request1.attempts <= 50)
        }

        // First request should have been retried exactly 50 times
        assertEquals(50, request1.attempts)

        // Upon final failure, request 1 should have been dropped from the queue
        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assertNull(spyDataStore.fetch(request1.uuid))

        // Second request should have been attempted after the final failure of request 1
        verify(exactly = 1) { request2.send(any()) }
    }

    @Test
    fun `503 results in a retry using exponential backoff`() {
        // same as above test but with a 503 instead of a 429
        // First unsent request, which we will retry till max attempts (calculated by exponential)
        // note that we are not sending the retry-after header on a 503
        val request1 = mockRequest("uuid-retry", KlaviyoApiRequest.Status.Unsent, 503)

        every { request1.state } answers {
            when (request1.attempts) {
                0 -> KlaviyoApiRequest.Status.Unsent.name
                20 -> KlaviyoApiRequest.Status.Failed.name
                else -> KlaviyoApiRequest.Status.PendingRetry.name
            }
        }

        // Second unset request in queue to ensure which shouldn't sent until first has failed
        val request2 = mockRequest("uuid-unsent", KlaviyoApiRequest.Status.Unsent)

        // Enqueue 2 requests
        KlaviyoApiClient.enqueueRequest(request1, request2)

        // Enqueueing should invoke handler.post and initialize our postedJob property
        assertNotNull(postedJob)

        // But the clock has not advanced, so no requests should have been sent yet
        assertEquals(0, request1.attempts)

        while (request1.state != KlaviyoApiRequest.Status.Failed.name) {
            val startAttempts = request1.attempts

            // Advance the time with our expected backoff interval
            staticClock.time += 50_000L

            // Run after advancing the clock (this mimics how handler.postDelay would run jobs)
            postedJob!!.run()

            // It should have attempted one send if the correct time elapsed
            assertEquals(startAttempts + 1, request1.attempts)

            // Check for repeat request but not above max
            assert(request1.attempts in 1 until 21)
        }

        // First request should have been retried exactly 20 times
        assertEquals(20, request1.attempts)

        // Upon final failure, request 1 should have been dropped from the queue
        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assertNull(spyDataStore.fetch(request1.uuid))

        // Second request should have been attempted after the final failure of request 1
        verify(exactly = 1) { request2.send(any()) }
    }

    @Test
    fun `Rate limited requests are retried with backoff until max attempts in absence of Retry-After header`() {
        val defaultInterval =
            Registry.config.networkFlushIntervals[NetworkMonitor.NetworkType.Wifi.position]

        // First unsent request, which we will retry till max attempts
        val request1 = mockRequest("uuid-retry", KlaviyoApiRequest.Status.Unsent).also {
            every { it.responseHeaders } returns emptyMap()
        }

        every { request1.state } answers {
            when (request1.attempts) {
                0 -> KlaviyoApiRequest.Status.Unsent.name
                50 -> KlaviyoApiRequest.Status.Failed.name
                else -> KlaviyoApiRequest.Status.PendingRetry.name
            }
        }

        // Second unset request in queue to ensure which shouldn't sent until first has failed
        val request2 = mockRequest("uuid-unsent", KlaviyoApiRequest.Status.Unsent)

        // Enqueue 2 requests
        KlaviyoApiClient.enqueueRequest(request1, request2)

        // Enqueueing should invoke handler.post and initialize our postedJob property
        assertNotNull(postedJob)

        // But the clock has not advanced, so no requests should have been sent yet
        assertEquals(0, request1.attempts)

        while (request1.state != KlaviyoApiRequest.Status.Failed.name) {
            val startAttempts = request1.attempts

            // Advance the time with our expected backoff interval
            staticClock.time += listOf(
                defaultInterval, // First attempt starts after default interval
                defaultInterval, // First RETRY starts after default interval bc 2s < 10s
                defaultInterval, // Second RETRY starts after default interval bc 4s < 10s
                defaultInterval, // Third RETRY starts after default interval bc 8s < 10s
                16_000L, // Exp. backoff time should be used bc 16s > 10s
                32_000L, // Exp. backoff time should be used bc 32s > 10s
                64_000L, // Exp. backoff time should be used bc 64s > 10s
                128_000L // Exp. backoff time should be used bc 128s > 10s
            ).getOrElse(startAttempts) { 180_000L } // Max backoff time should be used from here on, because 256s > 180s

            // Run after advancing the clock (this mimics how handler.postDelay would run jobs)
            postedJob!!.run()

            // It should have attempted one send if the correct time elapsed
            assertEquals(startAttempts + 1, request1.attempts)

            // Fail test if we exceed max attempts
            assert(request1.attempts <= 50)
        }

        // First request should have been retried exactly 50 times
        assertEquals(50, request1.attempts)

        // Upon final failure, request 1 should have been dropped from the queue
        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assertNull(spyDataStore.fetch(request1.uuid))

        // Second request should have been attempted after the final failure of request 1
        verify(exactly = 1) { request2.send(any()) }
    }

    @Test
    fun `Network requests are persisted to disk`() {
        spyDataStore.clear("mock_uuid1")
        spyDataStore.clear("mock_uuid2")

        KlaviyoApiClient.enqueueRequest(
            mockRequest("mock_uuid1"),
            mockRequest("mock_uuid2")
        )

        assertNotEquals(null, spyDataStore.fetch("mock_uuid1"))
        assertNotEquals(null, spyDataStore.fetch("mock_uuid2"))
        assertEquals(
            "[\"mock_uuid1\",\"mock_uuid2\"]",
            spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY)
        )
    }

    @Test
    fun `Flushing queue empties persistent store`() {
        spyDataStore.store("something_else", "test")
        spyDataStore.clear(KlaviyoApiClient.QUEUE_KEY)
        KlaviyoApiClient.enqueueRequest(mockRequest("mock_uuid"))

        assertNotEquals(null, spyDataStore.fetch("mock_uuid"))
        assertEquals("[\"mock_uuid\"]", spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY))

        KlaviyoApiClient.flushQueue()

        assertEquals(null, spyDataStore.fetch("mock_uuid"))
        assertEquals("[]", spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY))
        assertEquals("test", spyDataStore.fetch("something_else"))
    }

    @Test
    fun `Restores queue from persistent store`() {
        mockkObject(KlaviyoApiRequestDecoder)
        every { KlaviyoApiRequestDecoder.fromJson(any()) } answers { a ->
            val uuid = (a.invocation.args[0] as JSONObject).getString("uuid")
            mockRequest(uuid)
        }

        val expectedQueue = "[\"mock_uuid1\",\"mock_uuid2\"]"
        spyDataStore.store(KlaviyoApiClient.QUEUE_KEY, expectedQueue)
        spyDataStore.store("mock_uuid1", mockRequest("mock_uuid1").toString())
        spyDataStore.store("mock_uuid2", mockRequest("mock_uuid2").toString())

        KlaviyoApiClient.restoreQueue()
        val actualQueue = spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY)

        assertEquals(2, KlaviyoApiClient.getQueueSize())
        assertEquals(expectedQueue, actualQueue) // Expect same order in the queue
    }

    @Test
    fun `Handles bad JSON queue gracefully`() {
        spyDataStore.store(KlaviyoApiClient.QUEUE_KEY, "{}") // Bad JSON, isn't an array as expected

        KlaviyoApiClient.restoreQueue()
        val actualQueue = spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY)

        assertEquals(0, KlaviyoApiClient.getQueueSize())
        assertEquals("[]", actualQueue) // Expect the persisted queue to be emptied
    }

    @Test
    fun `Handles bad queue item JSON gracefully`() {
        mockkObject(KlaviyoApiRequestDecoder)
        every { KlaviyoApiRequestDecoder.fromJson(any()) } answers { a ->
            val uuid = (a.invocation.args[0] as JSONObject).getString("uuid")
            mockRequest(uuid)
        }

        val jsonArray = "[\"mock_uuid1\",\"mock_uuid2\",\"mock_uuid3\"]"
        spyDataStore.store(KlaviyoApiClient.QUEUE_KEY, jsonArray)
        spyDataStore.store("mock_uuid1", "{/}") // bad JSON!
        spyDataStore.store("mock_uuid2", mockRequest("mock_uuid2").toString())

        KlaviyoApiClient.restoreQueue()
        val actualQueue = spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY)

        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assertEquals("[\"mock_uuid2\"]", actualQueue) // Expect queue to reflect the dropped item
        assertNull(spyDataStore.fetch("mock_uuid1")) // Expect the item to be cleared from store
    }

    @Test
    fun `response body handles null stream correctly`() {
        // Create a KlaviyoApiRequest with required arguments
        val request = KlaviyoApiRequest("mockPath", RequestMethod.GET)

        // Spy on that specific instance
        val spiedRequest = spyk(request)

        // Mock network connection and config
        every { Registry.networkMonitor.isNetworkConnected() } returns true
        // Mock URL and URLConnection
        val urlMock = mockk<URL>()
        val connectionMock = mockk<HttpURLConnection>()

        // Set up mocks for URL and connection
        every { urlMock.openConnection() } returns connectionMock
        every { urlMock.protocol } returns "http"
        every { connectionMock.inputStream } returns null
        every { connectionMock.errorStream } returns null
        every { connectionMock.responseCode } returns HttpURLConnection.HTTP_INTERNAL_ERROR
        every { connectionMock.headerFields } returns emptyMap()
        every { connectionMock.setRequestProperty(any(), any()) } returns Unit
        every { connectionMock.requestMethod = any() } returns Unit
        every { connectionMock.readTimeout = any() } returns Unit
        every { connectionMock.connectTimeout = any() } returns Unit
        every { connectionMock.disconnect() } just runs
        every { connectionMock.connect() } just runs

        // Mock the URL used by the request to our mocked URL
        every { spiedRequest.url } returns urlMock

        try {
            // Attempt to send without an exception occurring
            spiedRequest.send()
        } catch (e: NullPointerException) {
            fail("NullPointerException was thrown: ${e.message}")
        }
    }

    private val trackingUrl = "https://klaviyo.com/track?id=123"
    private val profile = Profile().setAnonymousId(ANON_ID)

    /**
     * Utility function to set up common mocks for resolveDestinationUrl tests
     */
    private fun setupResolveDestinationUrlTest(
        requestStatus: KlaviyoApiRequest.Status,
        destinationUrl: Uri? = null
    ) {
        val expectedResponse = if (destinationUrl is Uri) {
            ResolveDestinationResult.Success(destinationUrl, trackingUrl)
        } else if (requestStatus == KlaviyoApiRequest.Status.Unsent) {
            ResolveDestinationResult.Unavailable(trackingUrl)
        } else {
            ResolveDestinationResult.Failure(trackingUrl)
        }

        mockkConstructor(UniversalClickTrackRequest::class)
        every { anyConstructed<UniversalClickTrackRequest>().uuid } returns "mock_resolve_destination_uuid"
        every { anyConstructed<UniversalClickTrackRequest>().send(any()) } returns requestStatus
        every { anyConstructed<UniversalClickTrackRequest>().getResult() } returns expectedResponse
        every { anyConstructed<UniversalClickTrackRequest>().headers } answers { callOriginal() }
        every { anyConstructed<UniversalClickTrackRequest>().prepareToEnqueue() } answers {
            callOriginal()
        }
    }

    private fun verifyEnqueuedClickTrack(inverse: Boolean = false) = if (inverse) {
        assertEquals(0, KlaviyoApiClient.getQueueSize())
        verify(inverse = true) { anyConstructed<UniversalClickTrackRequest>().prepareToEnqueue() }
    } else {
        verify { anyConstructed<UniversalClickTrackRequest>().prepareToEnqueue() }
        val actualQueue = spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY)
        assertEquals("[\"mock_resolve_destination_uuid\"]", actualQueue)
    }

    private fun executeResolveDestinationUrl(testScheduler: TestCoroutineScheduler): ResolveDestinationResult? {
        var callbackResult: ResolveDestinationResult? = null

        KlaviyoApiClient.resolveDestinationUrl(trackingUrl, profile) { result ->
            callbackResult = result
        }

        // Wait for coroutine to complete
        testScheduler.advanceUntilIdle()

        return callbackResult
    }

    @Test
    fun `resolveDestinationUrl invokes callback with Success when request succeeds`() = runTest(
        dispatcher
    ) {
        val destinationUrl = mockk<Uri>()
        setupResolveDestinationUrlTest(KlaviyoApiRequest.Status.Complete, destinationUrl)

        val result = executeResolveDestinationUrl(testScheduler)
        assert(result is ResolveDestinationResult.Success)
        assertEquals(destinationUrl, (result as ResolveDestinationResult.Success).destinationUrl)
        verifyEnqueuedClickTrack(inverse = true)

        unmockkConstructor(UniversalClickTrackRequest::class)
    }

    @Test
    fun `resolveDestinationUrl invokes callback with Unavailable and enqueues request when offline`() =
        runTest(dispatcher) {
            setupResolveDestinationUrlTest(KlaviyoApiRequest.Status.Unsent)

            assert(
                executeResolveDestinationUrl(testScheduler) is ResolveDestinationResult.Unavailable
            )
            verifyEnqueuedClickTrack()

            unmockkConstructor(UniversalClickTrackRequest::class)
        }

    @Test
    fun `resolveDestinationUrl invokes callback with Failure when request fails`() = runTest(
        dispatcher
    ) {
        setupResolveDestinationUrlTest(KlaviyoApiRequest.Status.Failed)

        assert(executeResolveDestinationUrl(testScheduler) is ResolveDestinationResult.Failure)
        verifyEnqueuedClickTrack(inverse = true)

        unmockkConstructor(UniversalClickTrackRequest::class)
    }

    @Test
    fun `resolveDestinationUrl raises config exceptions before coroutine scope`() = runTest(
        dispatcher
    ) {
        var called = false
        setupResolveDestinationUrlTest(KlaviyoApiRequest.Status.Failed)
        every { anyConstructed<UniversalClickTrackRequest>().headers } answers {
            // Mock some part of the constructor failing due to missing config
            called = true
            throw MissingConfig()
        }

        // Verify that the config exception is catchable by the caller
        assertNull(safeCall { executeResolveDestinationUrl(testScheduler) })
        assert(called)

        verifyEnqueuedClickTrack(inverse = true)

        unmockkConstructor(UniversalClickTrackRequest::class)
    }

    @Test
    fun `resolveDestinationUrl creates UniversalClickTrackRequest with correct parameters`() =
        runTest(
            dispatcher
        ) {
            val mockHeaders = spyk<MutableMap<String, String>>()
            setupResolveDestinationUrlTest(
                KlaviyoApiRequest.Status.Complete,
                mockk()
            )
            every { anyConstructed<UniversalClickTrackRequest>().headers } returns mockHeaders

            executeResolveDestinationUrl(testScheduler)

            verify { anyConstructed<UniversalClickTrackRequest>().baseUrl = trackingUrl }
            verify { mockHeaders.put(any(), any()) }

            unmockkConstructor(UniversalClickTrackRequest::class)
        }
}

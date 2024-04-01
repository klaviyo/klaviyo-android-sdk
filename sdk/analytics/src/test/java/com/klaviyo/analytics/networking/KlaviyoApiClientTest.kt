package com.klaviyo.analytics.networking

import android.os.Handler
import android.os.HandlerThread
import com.klaviyo.analytics.DeviceProperties
import com.klaviyo.analytics.DevicePropertiesTest
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.KlaviyoApiClient.HandlerUtil as HandlerUtil
import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequestDecoder
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.core.networking.NetworkMonitor
import com.klaviyo.core.networking.NetworkObserver
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.StaticClock
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import java.net.URL
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class KlaviyoApiClientTest : BaseTest() {

    private val flushIntervalWifi = 10_000L
    private val flushIntervalCell = 20_000L
    private val flushIntervalOffline = 30_000L
    private val queueDepth = 10
    private var postedJob: KlaviyoApiClient.NetworkRunnable? = null
    private val staticClock = StaticClock(TIME, ISO_TIME)

    private companion object {
        private val slotOnActivityEvent = slot<ActivityObserver>()
        private val slotOnNetworkChange = slot<NetworkObserver>()
        private val mockHandler = mockk<Handler>()
    }

    @Before
    override fun setup() {
        super.setup()

        DevicePropertiesTest.mockDeviceProperties()
        every { DeviceProperties.buildEventMetaData() } returns emptyMap()
        every { DeviceProperties.buildMetaData() } returns emptyMap()

        postedJob = null

        every { Registry.clock } returns staticClock
        every { configMock.networkFlushIntervals } returns longArrayOf(
            flushIntervalWifi,
            flushIntervalCell,
            flushIntervalOffline
        )
        every { configMock.networkFlushDepth } returns queueDepth
        every { networkMonitorMock.isNetworkConnected() } returns false
        every { networkMonitorMock.getNetworkType() } returns NetworkMonitor.NetworkType.Wifi
        every { lifecycleMonitorMock.onActivityEvent(capture(slotOnActivityEvent)) } returns Unit
        every { networkMonitorMock.onNetworkChange(capture(slotOnNetworkChange)) } returns Unit

        mockkObject(HandlerUtil)
        every { HandlerUtil.getHandler(any()) } returns mockHandler.apply {
            every { removeCallbacksAndMessages(any()) } returns Unit
            every { post(any()) } answers { a ->
                postedJob = (a.invocation.args[0] as KlaviyoApiClient.NetworkRunnable)
                postedJob!!.run().let { true }
            }
            every { postDelayed(any(), any()) } answers { a ->
                postedJob = a.invocation.args[0] as KlaviyoApiClient.NetworkRunnable
                true
            }
        }
        every { HandlerUtil.getHandlerThread(any()) } returns mockk<HandlerThread>().apply {
            every { start() } returns Unit
            every { looper } returns mockk()
            every { state } returns Thread.State.NEW
        }
    }

    @After
    override fun cleanup() {
        dataStoreSpy.clear(KlaviyoApiClient.QUEUE_KEY)
        KlaviyoApiClient.restoreQueue()
        assertEquals(0, KlaviyoApiClient.getQueueSize())
        super.cleanup()
        unmockkObject(KlaviyoApiClient)
        unmockkObject(KlaviyoApiRequestDecoder)
        unmockkObject(HandlerUtil)
    }

    private fun mockRequest(
        uuid: String = "uuid",
        status: KlaviyoApiRequest.Status = KlaviyoApiRequest.Status.Complete
    ): KlaviyoApiRequest =
        mockk<KlaviyoApiRequest>().also {
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
            every { it.responseHeaders } returns null
            every { it.responseBody } returns null
            every { it.responseCode } answers {
                when (getState()) {
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
    }

    @Test
    fun `Enqueues a push token API call`() {
        assertEquals(0, KlaviyoApiClient.getQueueSize())

        KlaviyoApiClient.enqueuePushToken(
            PUSH_TOKEN,
            Profile().setAnonymousId(ANON_ID)
        )

        assertEquals(1, KlaviyoApiClient.getQueueSize())
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
        verify(exactly = 1) { logSpy.verbose("Persisting queue") }
    }

    @Test
    fun `Enqueues an event API call`() {
        assertEquals(0, KlaviyoApiClient.getQueueSize())

        KlaviyoApiClient.enqueueEvent(
            Event(EventMetric.CUSTOM("mock")),
            Profile().setAnonymousId(ANON_ID)
        )

        assertEquals(1, KlaviyoApiClient.getQueueSize())
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
        verify { logSpy.verbose(match { it.contains("queue") }) }
    }

    @Test
    fun `Invokes callback and logs when request enqueued`() {
        var cbRequest: ApiRequest? = null
        KlaviyoApiClient.onApiRequest { cbRequest = it }

        val request = mockRequest(status = KlaviyoApiRequest.Status.Unsent)
        KlaviyoApiClient.enqueueRequest(request)
        assertEquals(request, cbRequest)
        verify { logSpy.verbose(match { it.contains("queue") }) }
    }

    @Test
    fun `Invokes callback and logs when request sent`() {
        every { configMock.networkFlushDepth } returns 1
        val request = mockRequest()
        KlaviyoApiClient.enqueueRequest(request)

        var cbRequest: ApiRequest? = null
        KlaviyoApiClient.onApiRequest { cbRequest = it }

        postedJob!!.run()
        assertEquals(request, cbRequest)
        verify { logSpy.verbose(match { it.contains("succeed") }) }
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
        assertNull(dataStoreSpy.fetch(fail))
    }

    @Test
    fun `An unsent request is not removed from the queue`() {
        val uuid = "uuid-failed"
        KlaviyoApiClient.enqueueRequest(mockRequest(uuid, KlaviyoApiRequest.Status.Unsent))

        assertEquals(1, KlaviyoApiClient.getQueueSize())
        KlaviyoApiClient.flushQueue()

        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assertNotNull(dataStoreSpy.fetch(uuid))
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
        assertNull(dataStoreSpy.fetch(request1.uuid))

        // Second request should have been attempted after the final failure of request 1
        verify(exactly = 1) { request2.send(any()) }
    }

    fun `Rate limited requests are retried with a backoff until max attempts`() {
        val defaultInterval = Registry.config.networkFlushIntervals[NetworkMonitor.NetworkType.Wifi.position]

        // First unsent request, which we will retry till max attempts
        val request1 = mockRequest("uuid-retry", KlaviyoApiRequest.Status.Unsent)
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
        assertNull(dataStoreSpy.fetch(request1.uuid))

        // Second request should have been attempted after the final failure of request 1
        verify(exactly = 1) { request2.send(any()) }
    }

    @Test
    fun `Network requests are persisted to disk`() {
        dataStoreSpy.clear("mock_uuid1")
        dataStoreSpy.clear("mock_uuid2")

        KlaviyoApiClient.enqueueRequest(
            mockRequest("mock_uuid1"),
            mockRequest("mock_uuid2")
        )

        assertNotEquals(null, dataStoreSpy.fetch("mock_uuid1"))
        assertNotEquals(null, dataStoreSpy.fetch("mock_uuid2"))
        assertEquals(
            "[\"mock_uuid1\",\"mock_uuid2\"]",
            dataStoreSpy.fetch(KlaviyoApiClient.QUEUE_KEY)
        )
    }

    @Test
    fun `Flushing queue empties persistent store`() {
        dataStoreSpy.store("something_else", "test")
        dataStoreSpy.clear(KlaviyoApiClient.QUEUE_KEY)
        KlaviyoApiClient.enqueueRequest(mockRequest("mock_uuid"))

        assertNotEquals(null, dataStoreSpy.fetch("mock_uuid"))
        assertEquals("[\"mock_uuid\"]", dataStoreSpy.fetch(KlaviyoApiClient.QUEUE_KEY))

        KlaviyoApiClient.flushQueue()

        assertEquals(null, dataStoreSpy.fetch("mock_uuid"))
        assertEquals("[]", dataStoreSpy.fetch(KlaviyoApiClient.QUEUE_KEY))
        assertEquals("test", dataStoreSpy.fetch("something_else"))
    }

    @Test
    fun `Restores queue from persistent store`() {
        mockkObject(KlaviyoApiRequestDecoder)
        every { KlaviyoApiRequestDecoder.fromJson(any()) } answers { a ->
            val uuid = (a.invocation.args[0] as JSONObject).getString("uuid")
            mockRequest(uuid)
        }

        val expectedQueue = "[\"mock_uuid1\",\"mock_uuid2\"]"
        dataStoreSpy.store(KlaviyoApiClient.QUEUE_KEY, expectedQueue)
        dataStoreSpy.store("mock_uuid1", mockRequest("mock_uuid1").toString())
        dataStoreSpy.store("mock_uuid2", mockRequest("mock_uuid2").toString())

        KlaviyoApiClient.restoreQueue()
        val actualQueue = dataStoreSpy.fetch(KlaviyoApiClient.QUEUE_KEY)

        assertEquals(2, KlaviyoApiClient.getQueueSize())
        assertEquals(expectedQueue, actualQueue) // Expect same order in the queue
    }

    @Test
    fun `Handles bad JSON queue gracefully`() {
        dataStoreSpy.store(KlaviyoApiClient.QUEUE_KEY, "{}") // Bad JSON, isn't an array as expected

        KlaviyoApiClient.restoreQueue()
        val actualQueue = dataStoreSpy.fetch(KlaviyoApiClient.QUEUE_KEY)

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
        dataStoreSpy.store(KlaviyoApiClient.QUEUE_KEY, jsonArray)
        dataStoreSpy.store("mock_uuid1", "{/}") // bad JSON!
        dataStoreSpy.store("mock_uuid2", mockRequest("mock_uuid2").toString())

        KlaviyoApiClient.restoreQueue()
        val actualQueue = dataStoreSpy.fetch(KlaviyoApiClient.QUEUE_KEY)

        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assertEquals("[\"mock_uuid2\"]", actualQueue) // Expect queue to reflect the dropped item
        assertNull(dataStoreSpy.fetch("mock_uuid1")) // Expect the item to be cleared from store
    }
}

package com.klaviyo.analytics.networking

import android.os.Handler
import android.os.HandlerThread
import com.klaviyo.analytics.DeviceProperties
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventType
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.KlaviyoApiClient.HandlerUtil as HandlerUtil
import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.networking.requests.BaseRequestTest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequestDecoder
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.core.networking.NetworkMonitor
import com.klaviyo.core.networking.NetworkObserver
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

internal class KlaviyoApiClientTest : BaseRequestTest() {
    private val flushIntervalWifi = 10_000
    private val flushIntervalCell = 20_000
    private val flushIntervalOffline = 30_000
    private val queueDepth = 10
    private var delayedRunner: KlaviyoApiClient.NetworkRunnable? = null
    private val staticClock = StaticClock(TIME, ISO_TIME)

    private companion object {
        private val slotOnActivityEvent = slot<ActivityObserver>()
        private val slotOnNetworkChange = slot<NetworkObserver>()
        private val mockHandler = mockk<Handler>()
    }

    @Before
    override fun setup() {
        super.setup()

        every { DeviceProperties.buildEventMetaData() } returns emptyMap()
        every { DeviceProperties.buildMetaData() } returns emptyMap()

        delayedRunner = null

        every { Registry.clock } returns staticClock
        every { configMock.networkFlushIntervals } returns intArrayOf(
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
                (a.invocation.args[0] as KlaviyoApiClient.NetworkRunnable).run()
                true
            }
            every { postDelayed(any(), any()) } answers { a ->
                delayedRunner = a.invocation.args[0] as KlaviyoApiClient.NetworkRunnable
                true
            }
        }
        every { HandlerUtil.getHandlerThread(any()) } returns mockk<HandlerThread>().apply {
            every { start() } returns Unit
            every { looper } returns mockk()
        }
    }

    private fun mockRequest(
        uuid: String = "uuid",
        status: KlaviyoApiRequest.Status = KlaviyoApiRequest.Status.Complete
    ): KlaviyoApiRequest =
        mockk<KlaviyoApiRequest>().also {
            every { it.uuid } returns uuid
            every { it.type } returns "Mock"
            every { it.state } returns status.name
            every { it.httpMethod } returns "GET"
            every { it.url } returns URL("https://mock.com")
            every { it.headers } returns mapOf("headerKey" to "headerValue")
            every { it.query } returns mapOf("queryKey" to "queryValue")
            every { it.responseBody } returns null
            every { it.send(any()) } returns status
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
    fun `Enqueues an event API call`() {
        assertEquals(0, KlaviyoApiClient.getQueueSize())

        KlaviyoApiClient.enqueueEvent(
            Event(EventType.CUSTOM("mock")),
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
        verify { logSpy.debug(match { it.contains("queue") }) }
    }

    @Test
    fun `Invokes callback and logs when request enqueued`() {
        var cbRequest: ApiRequest? = null
        KlaviyoApiClient.onApiRequest { cbRequest = it }

        val request = mockRequest(status = KlaviyoApiRequest.Status.Unsent)
        KlaviyoApiClient.enqueueRequest(request)
        assertEquals(request, cbRequest)
        verify { logSpy.debug(match { it.contains("queue") }) }
    }

    @Test
    fun `Invokes callback and logs when request sent`() {
        every { configMock.networkFlushDepth } returns 1
        val request = mockRequest()
        KlaviyoApiClient.enqueueRequest(request)

        var cbRequest: ApiRequest? = null
        KlaviyoApiClient.onApiRequest { cbRequest = it }

        delayedRunner!!.run()
        assertEquals(request, cbRequest)
        verify { logSpy.info(match { it.contains("complete") }) }
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

        delayedRunner!!.run()

        assertEquals(0, KlaviyoApiClient.getQueueSize())
    }

    @Test
    fun `Flushes queue when configured time has elapsed`() {
        val requestMock = mockRequest()

        KlaviyoApiClient.enqueueRequest(requestMock)

        staticClock.execute(flushIntervalWifi.toLong())

        delayedRunner!!.run()

        assertEquals(0, KlaviyoApiClient.getQueueSize())
    }

    @Test
    fun `Does not flush queue if no criteria is met`() {
        repeat(queueDepth - 1) {
            KlaviyoApiClient.enqueueRequest(mockRequest("uuid-$it"))
            assertEquals(it + 1, KlaviyoApiClient.getQueueSize())
        }

        delayedRunner!!.run()

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
    fun `Rate limited requests are retried with a backoff`() {
        val request1 = mockRequest("uuid-retry", KlaviyoApiRequest.Status.PendingRetry)
        val request2 = mockRequest("uuid-unsent", KlaviyoApiRequest.Status.Unsent)
        var attempts = 0
        var backoffTime = flushIntervalWifi
        every { request1.attempts } answers { attempts }

        KlaviyoApiClient.enqueueRequest(request1, request2)

        val job = KlaviyoApiClient.NetworkRunnable()

        while (request1.attempts < configMock.networkMaxRetries) {
            // Run before advancing the clock: it shouldn't attempt any sends
            job.run()
            verify(exactly = attempts) { request1.send(any()) }

            attempts++

            // Advance the time with increasing backoff interval
            backoffTime *= attempts
            staticClock.time += backoffTime
            delayedRunner = null

            job.run()
            assertNotNull(delayedRunner)
            assertEquals(2, KlaviyoApiClient.getQueueSize())
            assertNotNull(dataStoreSpy.fetch(request1.uuid))
            assertNotNull(dataStoreSpy.fetch(request2.uuid))
            verify(exactly = attempts) { request1.send(any()) }
            verify(inverse = true) { request2.send(any()) }
        }
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

    @After
    fun cleanup() {
        dataStoreSpy.clear(KlaviyoApiClient.QUEUE_KEY)
        KlaviyoApiClient.restoreQueue()
        assertEquals(0, KlaviyoApiClient.getQueueSize())
        unmockkObject(KlaviyoApiClient)
        unmockkObject(KlaviyoApiRequestDecoder)
        unmockkObject(HandlerUtil)
    }
}

package com.klaviyo.coresdk.networking

import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import com.klaviyo.coresdk.BaseTest
import com.klaviyo.coresdk.Registry
import com.klaviyo.coresdk.config.StaticClock
import com.klaviyo.coresdk.lifecycle.ActivityEvent
import com.klaviyo.coresdk.lifecycle.ActivityObserver
import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.EventType
import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.networking.requests.KlaviyoApiRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class KlaviyoApiClientTest : BaseTest() {
    private val flushInterval = 1000
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

        delayedRunner = null

        every { Registry.clock } returns staticClock
        every { configMock.networkFlushInterval } returns flushInterval
        every { configMock.networkFlushDepth } returns queueDepth
        every { networkMonitorMock.isNetworkConnected() } returns false
        every { lifecycleMonitorMock.onActivityEvent(capture(slotOnActivityEvent)) } returns Unit
        every { networkMonitorMock.onNetworkChange(capture(slotOnNetworkChange)) } returns Unit

        mockkObject(KlaviyoApiClient.HandlerUtil)
        every { KlaviyoApiClient.HandlerUtil.getHandler(any()) } returns mockHandler.apply {
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
        every { KlaviyoApiClient.HandlerUtil.getHandlerThread(any()) } returns mockk<HandlerThread>().apply {
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
            every { it.send() } returns status
            every { it.toJson() } returns "{\"headers\":{\"headerKey\":\"headerValue\"},\"method\":\"GET\",\"query\":{\"queryKey\":\"queryValue\"},\"time\":\"time\",\"uuid\":\"$uuid\",\"url_path\":\"test\"}"
            every { it.equals(any()) } answers { a ->
                it.uuid == (a.invocation.args[0] as KlaviyoApiRequest).uuid
            }
        }

    @Test
    fun `Is registered service`() {
        // An odd case, since KlaviyoApiClient is very reliant on other services just to init,
        // I am simply testing that Registry will return my object mock
        mockkObject(KlaviyoApiClient)
        unmockkObject(Registry)
        assertEquals(KlaviyoApiClient, Registry.apiClient)
        setup() // Also have to reset the test conditions to that cleanup can run properly
    }

    @Test
    fun `Enqueues a profile API call`() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } returns "mock"

        assertEquals(0, KlaviyoApiClient.getQueueSize())

        KlaviyoApiClient.enqueueProfile(Profile().setEmail(EMAIL))

        assertEquals(1, KlaviyoApiClient.getQueueSize())
    }

    @Test
    fun `Enqueues an event API call`() {
        assertEquals(0, KlaviyoApiClient.getQueueSize())

        KlaviyoApiClient.enqueueEvent(
            Event(EventType.CUSTOM("mock")),
            Profile().setEmail(EMAIL)
        )

        assertEquals(1, KlaviyoApiClient.getQueueSize())
    }

    @Test
    fun `Stops handler thread on application stop`() {
        var callCount = 0
        KlaviyoApiClient.enqueueRequest(mockRequest())
        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assert(slotOnActivityEvent.isCaptured)
        every { mockHandler.removeCallbacksAndMessages(null) } answers {
            callCount++
        }

        slotOnActivityEvent.captured(ActivityEvent.Paused(mockk()))
        slotOnActivityEvent.captured(ActivityEvent.AllStopped())

        assertEquals(1, callCount)
    }

    @Test
    fun `Flushes queue on network restored`() {
        KlaviyoApiClient.enqueueRequest(mockRequest())
        staticClock.time += flushInterval
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
        staticClock.execute(flushInterval.toLong())

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

        KlaviyoApiClient.NetworkRunnable(true).run()

        assertEquals(0, KlaviyoApiClient.getQueueSize())
    }

    @Test
    fun `Failed requests are cleared from the queue`() {
        val fail = "uuid-failed"
        KlaviyoApiClient.enqueueRequest(mockRequest(fail, KlaviyoApiRequest.Status.Failed))

        KlaviyoApiClient.NetworkRunnable(true).run()

        assertEquals(0, KlaviyoApiClient.getQueueSize())
        assertNull(dataStoreSpy.fetch(fail))
    }

    @Test
    fun `Rate limited requests are retried with a backoff`() {
        val request1 = mockRequest("uuid-retry", KlaviyoApiRequest.Status.PendingRetry)
        val request2 = mockRequest("uuid-unsent", KlaviyoApiRequest.Status.Unsent)
        var attempts = 0
        var backoffTime = flushInterval
        every { request1.attempts } answers { attempts }

        KlaviyoApiClient.enqueueRequest(request1, request2)

        val job = KlaviyoApiClient.NetworkRunnable()

        while (request1.attempts < configMock.networkMaxRetries) {
            // Run before advancing the clock: it shouldn't attempt any sends
            job.run()
            verify(exactly = attempts) { request1.send() }

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
            verify(exactly = attempts) { request1.send() }
            verify(inverse = true) { request2.send() }
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

        KlaviyoApiClient.NetworkRunnable(true).run()

        assertEquals(null, dataStoreSpy.fetch("mock_uuid"))
        assertEquals("[]", dataStoreSpy.fetch(KlaviyoApiClient.QUEUE_KEY))
        assertEquals("test", dataStoreSpy.fetch("something_else"))
    }

    @Test
    fun `Restores queue from persistent store`() {
        mockkObject(KlaviyoApiRequest.Companion)
        every { KlaviyoApiRequest.Companion.fromJson(any()) } answers { a ->
            val uuid = (a.invocation.args[0] as JSONObject).getString("uuid")
            mockRequest(uuid)
        }

        val expectedQueue = "[\"mock_uuid1\",\"mock_uuid2\"]"
        dataStoreSpy.store(KlaviyoApiClient.QUEUE_KEY, expectedQueue)
        dataStoreSpy.store("mock_uuid1", mockRequest("mock_uuid1").toJson())
        dataStoreSpy.store("mock_uuid2", mockRequest("mock_uuid2").toJson())

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
        mockkObject(KlaviyoApiRequest.Companion)
        every { KlaviyoApiRequest.Companion.fromJson(any()) } answers { a ->
            val uuid = (a.invocation.args[0] as JSONObject).getString("uuid")
            mockRequest(uuid)
        }

        dataStoreSpy.store(KlaviyoApiClient.QUEUE_KEY, "[\"mock_uuid1\",\"mock_uuid2\"]")
        dataStoreSpy.store("mock_uuid1", "{/}") // bad JSON!
        dataStoreSpy.store("mock_uuid2", mockRequest("mock_uuid2").toJson())

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
        unmockkObject(KlaviyoApiRequest.Companion)
    }
}

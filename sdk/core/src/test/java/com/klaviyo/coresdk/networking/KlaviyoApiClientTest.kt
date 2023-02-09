package com.klaviyo.coresdk.networking

import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import com.klaviyo.coresdk.BaseTest
import com.klaviyo.coresdk.Registry
import com.klaviyo.coresdk.config.StaticClock
import com.klaviyo.coresdk.lifecycle.ActivityObserver
import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.KlaviyoEventType
import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.networking.requests.KlaviyoApiRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

internal class KlaviyoApiClientTest : BaseTest() {
    private val flushInterval = 1000
    private val queueDepth = 10
    private var delayedRunner: KlaviyoApiClient.NetworkRunnable? = null
    private val slotWhenStopped = slot<ActivityObserver>()
    private val slotWhenNetworkChanged = slot<NetworkObserver>()
    private val staticClock = StaticClock(TIME, ISO_TIME)

    @Before
    override fun setup() {
        super.setup()

        delayedRunner = null

        every { Registry.clock } returns staticClock
        every { configMock.networkFlushInterval } returns flushInterval
        every { configMock.networkFlushDepth } returns queueDepth
        every { networkMonitorMock.isNetworkConnected() } returns false
        every { lifecycleMonitorMock.onAllActivitiesStopped(capture(slotWhenStopped)) } returns Unit
        every { networkMonitorMock.onNetworkChange(capture(slotWhenNetworkChanged)) } returns Unit

        mockkObject(KlaviyoApiClient.HandlerUtil)
        every { KlaviyoApiClient.HandlerUtil.getHandler(any()) } returns mockk<Handler>().apply {
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

    private fun mockRequest(uuid: String = "uuid"): KlaviyoApiRequest =
        mockk<KlaviyoApiRequest>().also {
            every { it.uuid } returns uuid
            every { it.send() } returns "1"
            every { it.toJson() } returns "{\"headers\":{\"headerKey\":\"headerValue\"},\"method\":\"GET\",\"query\":{\"queryKey\":\"queryValue\"},\"time\":\"time\",\"uuid\":\"$uuid\",\"url_path\":\"test\"}"
            every { it.equals(any()) } answers { a ->
                it.uuid == (a.invocation.args[0] as KlaviyoApiRequest).uuid
            }
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
            KlaviyoEventType.CUSTOM("mock"),
            Event(),
            Profile().setEmail(EMAIL)
        )

        assertEquals(1, KlaviyoApiClient.getQueueSize())
    }

    @Test
    fun `Flushes queue on application stop`() {
        KlaviyoApiClient.startListeners()
        KlaviyoApiClient.enqueueRequest(mockRequest())
        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assert(slotWhenStopped.isCaptured)
        slotWhenStopped.captured(mockk()) // invoke the whenStopped listener
        assertEquals(0, KlaviyoApiClient.getQueueSize())
    }

    @Test
    fun `Flushes queue on network restored`() {
        KlaviyoApiClient.startListeners()
        KlaviyoApiClient.enqueueRequest(mockRequest())
        assertEquals(1, KlaviyoApiClient.getQueueSize())
        assert(slotWhenNetworkChanged.isCaptured)
        slotWhenNetworkChanged.captured(false)
        assertEquals(1, KlaviyoApiClient.getQueueSize())
        slotWhenNetworkChanged.captured(true)
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
    fun `Network requests are persisted to disk`() {
        dataStoreSpy.clear("mock_uuid1")
        dataStoreSpy.clear("mock_uuid2")

        KlaviyoApiClient.enqueueRequest(mockRequest("mock_uuid1"))
        KlaviyoApiClient.enqueueRequest(mockRequest("mock_uuid2"))

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
        assertEquals(null, dataStoreSpy.fetch(KlaviyoApiClient.QUEUE_KEY))
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

    @After
    fun cleanup() {
        KlaviyoApiClient.NetworkRunnable(force = true).run()
        assertEquals(0, KlaviyoApiClient.getQueueSize())
        dataStoreSpy.clear(KlaviyoApiClient.QUEUE_KEY)
        unmockkObject(KlaviyoApiRequest.Companion)
    }
}

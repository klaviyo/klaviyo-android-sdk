package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.helpers.BaseTest
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NetworkBatcherTest : BaseTest() {
    private val flushInterval = 1000
    private val queueDepth = 10

    @Before
    override fun setup() {
        super.setup()
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .applicationContext(contextMock)
            .networkFlushInterval(flushInterval)
            .networkFlushDepth(queueDepth)
            .build()
    }

    @After
    fun cleanup() {
        NetworkBatcher.NetworkRunnable(forceEmpty = true).run()
        assertEquals(0, NetworkBatcher.getBatchQueueSize())
    }

    @Test
    fun `empties the queue when full`() {
        val batcherSpy = spyk<NetworkBatcher>()

        every { batcherSpy.initBatcher() } returns Unit

        repeat(queueDepth - 1) {
            val requestMock = mockk<KlaviyoRequest>()
            every { requestMock.sendNetworkRequest() } returns "1"

            batcherSpy.batchRequests(requestMock)

            assertEquals(it + 1, batcherSpy.getBatchQueueSize())
        }

        val requestMock = mockk<KlaviyoRequest>()
        every { requestMock.sendNetworkRequest() } returns "1"

        batcherSpy.batchRequests(requestMock)
        NetworkBatcher.NetworkRunnable().run()

        assertEquals(0, batcherSpy.getBatchQueueSize())
    }

    @Test
    fun `Network Batcher empties the queue after timeout`() {
        val batcherSpy = spyk<NetworkBatcher>()
        val requestMock = mockk<KlaviyoRequest>()

        every { batcherSpy.initBatcher() } returns Unit
        every { requestMock.sendNetworkRequest() } returns "1"

        batcherSpy.batchRequests(requestMock)
        Thread.sleep(1000)
        NetworkBatcher.NetworkRunnable().run()

        assertEquals(0, batcherSpy.getBatchQueueSize())
    }

    @Test
    fun `Network Batcher accepts multiple requests per call`() {
        val batcherSpy = spyk<NetworkBatcher>()

        val requests = (0..5).map {
            mockk<KlaviyoRequest>().also {
                every { it.sendNetworkRequest() } returns "1"
            }
        }

        every { batcherSpy.initBatcher() } returns Unit

        batcherSpy.batchRequests(*requests.toTypedArray())
        assertEquals(requests.size, NetworkBatcher.getBatchQueueSize())
    }
}

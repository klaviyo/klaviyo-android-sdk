package com.klaviyo.coresdk.networking

import android.content.Context
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NetworkBatcherTest {
    private val contextMock = mock<Context>()

    @Before
    fun setup() {
        KlaviyoConfig.Builder()
                .apiKey("Fake_Key")
                .applicationContext(contextMock)
                .networkFlushInterval(1000)
                .networkFlushDepth(10)
                .build()
    }

    @Test
    fun `Network Batcher empties the queue when full`() {
        val batcherSpy = spy<NetworkBatcher>()

        doNothing().whenever(batcherSpy).initBatcher()

        for (i in 0..8) {
            val requestMock = mock<KlaviyoRequest>()

            batcherSpy.batchRequests(requestMock)

            assertEquals(i + 1, batcherSpy.getBatchQueueSize())
        }

        val requestMock = mock<KlaviyoRequest>()

        batcherSpy.batchRequests(requestMock)
        NetworkBatcher.NetworkRunnable().run()

        assertEquals(0, batcherSpy.getBatchQueueSize())
    }

    @Test
    fun `Network Batcher empties the queue after timeout`() {
        val batcherSpy = spy<NetworkBatcher>()
        val requestMock = mock<KlaviyoRequest>()

        doNothing().whenever(batcherSpy).initBatcher()

        batcherSpy.batchRequests(requestMock)
        Thread.sleep(1000)
        NetworkBatcher.NetworkRunnable().run()

        assertEquals(0, batcherSpy.getBatchQueueSize())
    }

    @Test
    fun `Network Batcher accepts multiple requests per call`() {
        val batcherSpy = spy<NetworkBatcher>()

        val requestMock = mock<KlaviyoRequest>()
        val requestMock2 = mock<KlaviyoRequest>()
        val requestMock3 = mock<KlaviyoRequest>()

        doNothing().whenever(batcherSpy).initBatcher()

        batcherSpy.batchRequests(requestMock, requestMock2, requestMock3)
        assertEquals(3, NetworkBatcher.getBatchQueueSize())
    }
}
package com.klaviyo.coresdk.networking.requests

import android.content.Context
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.LifecycleMonitor
import com.klaviyo.coresdk.networking.NetworkMonitor
import com.klaviyo.coresdk.networking.RequestMethod
import io.mockk.called
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NetworkRequestTest {
    private val contextMock = mockk<Context>()
    private val environmentMock = mockk<LifecycleMonitor>()
    private val networkMock = mockk<NetworkMonitor>()

    @Before
    fun setup() {
        mockkObject(Klaviyo.Registry)
        every { Klaviyo.Registry.lifecycleMonitor } returns environmentMock
        every { Klaviyo.Registry.networkMonitor } returns networkMock
        every { networkMock.isNetworkConnected() } returns true

        KlaviyoConfig.Builder()
            .apiKey("Fake_Key")
            .applicationContext(contextMock)
            .networkTimeout(1000)
            .networkFlushInterval(10000)
            .build()
    }

    @Test
    fun `build url handles empty query data`() {
        val urlString = "https://valid.url"
        val request = spyk<NetworkRequest>()
        every { request.queryData } returns emptyMap()
        every { request.urlString } returns urlString

        val expectedUrl = URL("$urlString?")

        val actualUrl = request.buildURL()

        assertEquals(expectedUrl, actualUrl)
    }

    @Test
    fun `build url handles multiple query data`() {
        val urlString = "https://valid.url"
        val request = spyk<NetworkRequest>()
        every { request.queryData } returns mapOf("first" to "second", "query" to "1", "flag" to "false")
        every { request.urlString } returns urlString

        val expectedUrl = URL("$urlString?first=second&query=1&flag=false")

        val actualUrl = request.buildURL()

        assertEquals(expectedUrl, actualUrl)
    }

    @Test
    fun `sendNetworkRequest returns null when no internet`() {
        every { networkMock.isNetworkConnected() } returns false

        val expectedResponse = null
        val request = spyk<NetworkRequest>()
        val actualResponse = request.sendNetworkRequest()

        assertEquals(expectedResponse, actualResponse)
    }

    @Test
    fun `sendNetworkRequest uses URL and config for GET`() {
        mockkObject(KlaviyoConfig)
        val connectionMock = mockk<HttpURLConnection>()
        val request = spyk<NetworkRequest>()
        val url = URL("https://valid.url?first=second&query=1&flag=false")
        val expectedResponse = "something"
        every { KlaviyoConfig.networkTimeout } returns 1
        every { request.buildURL() } returns url
        every { request.buildConnection(url) } returns connectionMock
        every { request.requestMethod } returns RequestMethod.GET
        every { request.appendHeaders(connectionMock) } returns Unit
        every { request.readResponse(connectionMock) } returns expectedResponse
        every { connectionMock.readTimeout = any() } returns Unit
        every { connectionMock.connectTimeout = any() } returns Unit
        every { connectionMock.requestMethod = any() } returns Unit
        every { connectionMock.requestMethod } returns RequestMethod.GET.name
        every { connectionMock.connect() } returns Unit

        val actualResponse = request.sendNetworkRequest()

        assertEquals(expectedResponse, actualResponse)
    }

    @Test
    fun `sendNetworkRequest uses URL, config, and payload for POST when payload is not empty`() {
        mockkObject(KlaviyoConfig)
        val connectionMock = mockk<HttpURLConnection>()
        val outputMock = spyk(OutputStream.nullOutputStream())
        val request = spyk<NetworkRequest>()
        val url = URL("https://valid.url?first=second&query=1&flag=false")
        val payload = "some payload"
        val expectedResponse = "something"
        every { KlaviyoConfig.networkTimeout } returns 1
        every { request.buildURL() } returns url
        every { request.buildConnection(url) } returns connectionMock
        every { request.requestMethod } returns RequestMethod.POST
        every { request.payload } returns payload
        every { request.appendHeaders(connectionMock) } returns Unit
        every { request.readResponse(connectionMock) } returns expectedResponse
        every { connectionMock.readTimeout = any() } returns Unit
        every { connectionMock.connectTimeout = any() } returns Unit
        every { connectionMock.requestMethod = any() } returns Unit
        every { connectionMock.requestMethod } returns RequestMethod.POST.name
        every { connectionMock.doOutput = any() } returns Unit
        every { connectionMock.connect() } returns Unit
        every { connectionMock.outputStream } returns outputMock

        val actualResponse = request.sendNetworkRequest()

        assertEquals(expectedResponse, actualResponse)
        verify(exactly = 2) {
            request.payload
        }
    }

    @Test
    fun `sendNetworkRequest uses URL, config for POST when payload is empty`() {
        mockkObject(KlaviyoConfig)
        val connectionMock = mockk<HttpURLConnection>()
        val outputMock = mockk<OutputStream>()
        val request = spyk<NetworkRequest>()
        val url = URL("https://valid.url?first=second&query=1&flag=false")
        val expectedResponse = "something"
        every { KlaviyoConfig.networkTimeout } returns 1
        every { request.buildURL() } returns url
        every { request.buildConnection(url) } returns connectionMock
        every { request.requestMethod } returns RequestMethod.POST
        every { request.appendHeaders(connectionMock) } returns Unit
        every { request.readResponse(connectionMock) } returns expectedResponse
        every { connectionMock.readTimeout = any() } returns Unit
        every { connectionMock.connectTimeout = any() } returns Unit
        every { connectionMock.requestMethod = any() } returns Unit
        every { connectionMock.requestMethod } returns RequestMethod.POST.name
        every { connectionMock.doOutput = any() } returns Unit
        every { connectionMock.outputStream } returns outputMock
        every { connectionMock.connect() } returns Unit

        val actualResponse = request.sendNetworkRequest()

        assertEquals(expectedResponse, actualResponse)
        verify {
            outputMock wasNot called
        }
        verify(exactly = 1) {
            request.payload
        }
    }

    @Test
    fun `readResponse reads input stream when connection has a successful status code`() {
        val requestSpy = spyk<NetworkRequest>()
        val connectionMock = mockk<HttpsURLConnection>()
        every { connectionMock.disconnect() } returns Unit

        for (status in 200 until 300) {
            val inputStream = ByteArrayInputStream("1".toByteArray())

            every { connectionMock.inputStream } returns inputStream
            every { connectionMock.responseCode } returns status

            val response = requestSpy.readResponse(connectionMock)

            assertEquals("1", response)
        }
    }

    @Test
    fun `readResponse reads error stream when connection has a successful status code`() {
        val requestSpy = spyk<NetworkRequest>()
        val connectionMock = mockk<HttpsURLConnection>()
        every { connectionMock.disconnect() } returns Unit

        for (status in 300 until 600) {
            val errorStream = ByteArrayInputStream("Invalid params".toByteArray())

            every { connectionMock.errorStream } returns errorStream
            every { connectionMock.responseCode } returns status

            val response = requestSpy.readResponse(connectionMock)

            assertEquals("Invalid params", response)
        }
    }
}

package com.klaviyo.coresdk.networking.requests

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import com.klaviyo.coresdk.KlaviyoConfig
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

    @Before
    fun setup() {
        KlaviyoConfig.Builder()
            .apiKey("Fake_Key")
            .applicationContext(contextMock)
            .networkTimeout(1000)
            .networkFlushInterval(10000)
            .build()
    }

    @Test
    fun `isInternetConnected returns false when no active network info`() {
        val contextMock = mockk<Context>()
        val connectivityManagerMock = mockk<ConnectivityManager>()
        every { contextMock.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManagerMock
        every { connectivityManagerMock.activeNetworkInfo } returns null

        val expectedConnected = false

        val request = spyk<NetworkRequest>()
        val actualConnected = request.isInternetConnected(contextMock)

        assertEquals(expectedConnected, actualConnected)
    }

    @Test
    fun `isInternetConnected returns false when no network capabilities`() {
        val contextMock = mockk<Context>()
        val connectivityManagerMock = mockk<ConnectivityManager>()
        val networkInfoMock = mockk<NetworkInfo>()
        val networkMock = mockk<Network>()
        every { contextMock.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManagerMock
        every { connectivityManagerMock.activeNetworkInfo } returns networkInfoMock
        every { connectivityManagerMock.activeNetwork } returns networkMock
        every { connectivityManagerMock.getNetworkCapabilities(networkMock) } returns null

        val expectedConnected = false

        val request = spyk<NetworkRequest>()
        val actualConnected = request.isInternetConnected(contextMock)

        assertEquals(expectedConnected, actualConnected)
    }

    @Test
    fun `isInternetConnected returns false when not using internet`() {
        val contextMock = mockk<Context>()
        val connectivityManagerMock = mockk<ConnectivityManager>()
        val networkInfoMock = mockk<NetworkInfo>()
        val networkMock = mockk<Network>()
        val networkCapabilitiesMock = mockk<NetworkCapabilities>()
        every { contextMock.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManagerMock
        every { connectivityManagerMock.activeNetworkInfo } returns networkInfoMock
        every { connectivityManagerMock.activeNetwork } returns networkMock
        every { connectivityManagerMock.getNetworkCapabilities(networkMock) } returns networkCapabilitiesMock
        every { networkCapabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false

        val expectedConnected = false

        val request = spyk<NetworkRequest>()
        val actualConnected = request.isInternetConnected(contextMock)

        assertEquals(expectedConnected, actualConnected)
    }

    @Test
    fun `isInternetConnected returns false when using internet and not connected`() {
        val contextMock = mockk<Context>()
        val connectivityManagerMock = mockk<ConnectivityManager>()
        val networkInfoMock = mockk<NetworkInfo>()
        val networkMock = mockk<Network>()
        val networkCapabilitiesMock = mockk<NetworkCapabilities>()
        every { contextMock.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManagerMock
        every { connectivityManagerMock.activeNetworkInfo } returns networkInfoMock
        every { connectivityManagerMock.activeNetwork } returns networkMock
        every { connectivityManagerMock.getNetworkCapabilities(networkMock) } returns networkCapabilitiesMock
        every { networkCapabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { networkInfoMock.isConnectedOrConnecting } returns false

        val expectedConnected = false

        val request = spyk<NetworkRequest>()
        val actualConnected = request.isInternetConnected(contextMock)

        assertEquals(expectedConnected, actualConnected)
    }

    @Test
    fun `isInternetConnected returns true when using internet and connected`() {
        val contextMock = mockk<Context>()
        val connectivityManagerMock = mockk<ConnectivityManager>()
        val networkInfoMock = mockk<NetworkInfo>()
        val networkMock = mockk<Network>()
        val networkCapabilitiesMock = mockk<NetworkCapabilities>()
        every { contextMock.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManagerMock
        every { connectivityManagerMock.activeNetworkInfo } returns networkInfoMock
        every { connectivityManagerMock.activeNetwork } returns networkMock
        every { connectivityManagerMock.getNetworkCapabilities(networkMock) } returns networkCapabilitiesMock
        every { networkCapabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { networkInfoMock.isConnectedOrConnecting } returns true

        val expectedConnected = true

        val request = spyk<NetworkRequest>()
        val actualConnected = request.isInternetConnected(contextMock)

        assertEquals(expectedConnected, actualConnected)
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
        val expectedResponse = null

        val request = spyk<NetworkRequest>()

        every { request.isInternetConnected(any()) } returns false

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
        every { request.isInternetConnected(any()) } returns true
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
        every { request.isInternetConnected(any()) } returns true
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
        every { request.isInternetConnected(any()) } returns true
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

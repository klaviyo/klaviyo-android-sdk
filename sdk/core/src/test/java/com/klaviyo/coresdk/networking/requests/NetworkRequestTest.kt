package com.klaviyo.coresdk.networking.requests

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.RequestMethod
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.whenever
import java.io.ByteArrayInputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NetworkRequestTest {
    private val contextMock = mock<Context>()

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
    fun `Is internet connected given the application context`() {
        val contextSpy = spy<Context>()
        val connectivityManagerMock = mock<ConnectivityManager>()
        val networkInfoMock = mock<NetworkInfo>()
        val networkMock = mock<Network>()
        val networkCapabilitiesMock = mock<NetworkCapabilities>()
        val requestSpy = spy<NetworkRequest>()

        doReturn(connectivityManagerMock).whenever(contextSpy)
            .getSystemService(Context.CONNECTIVITY_SERVICE)
        whenever(connectivityManagerMock.activeNetworkInfo).thenReturn(networkInfoMock)
        whenever(connectivityManagerMock.activeNetwork).thenReturn(networkMock)
        whenever(connectivityManagerMock.getNetworkCapabilities(networkMock)).thenReturn(
            networkCapabilitiesMock
        )
        whenever(networkCapabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(
            true
        )
        whenever(networkInfoMock.isConnectedOrConnecting).thenReturn(true)

        val isConnected = requestSpy.isInternetConnected(contextSpy)

        assertEquals(true, isConnected)
    }

    @Test
    fun `Send network request with query information successfully`() {
        val queryData = mapOf(
            "key" to "value",
            "int" to "1"
        )
        val url = "https://www.some_url.com"
        val expectedUrlString = "$url?key=value&int=1"

        val requestSpy = spy<NetworkRequest>()
        val connectionMock = mock<HttpsURLConnection>()

        doReturn(RequestMethod.GET).whenever(requestSpy).requestMethod
        doReturn(queryData).whenever(requestSpy).queryData
        doReturn(url).whenever(requestSpy).urlString

        doReturn(true).whenever(requestSpy).isInternetConnected(any())
        doReturn(connectionMock).whenever(requestSpy).buildConnection(URL(expectedUrlString))
        doReturn("1").whenever(requestSpy).readResponse(connectionMock)

        val response = requestSpy.sendNetworkRequest()

        assertEquals("1", response)
    }

    @Test
    fun `Send network request without query information successfully`() {
        val requestSpy = spy<NetworkRequest>()
        val connectionMock = mock<HttpsURLConnection>()
        val queryData = emptyMap<String, String>()
        val url = "https://www.some_url.com"
        val expectedUrl = "$url?"

        doReturn(RequestMethod.GET).whenever(requestSpy).requestMethod
        doReturn(queryData).whenever(requestSpy).queryData
        doReturn(url).whenever(requestSpy).urlString

        doReturn(true).whenever(requestSpy).isInternetConnected(contextMock)
        doReturn(connectionMock).whenever(requestSpy).buildConnection(URL(expectedUrl))
        doReturn("1").whenever(requestSpy).readResponse(connectionMock)

        val response = requestSpy.sendNetworkRequest()

        assertEquals("1", response)
    }

    @Test
    fun `Send network request fails if internet is not active`() {
        val networkingSpy = spy<NetworkRequest>()

        doReturn(false).whenever(networkingSpy).isInternetConnected(contextMock)

        val response = networkingSpy.sendNetworkRequest()

        assertEquals(null, response)
    }

    @Test
    fun `Reads input stream when response contains a valid status code from HttpsURLConnection successfully`() {
        val requestSpy = spy<NetworkRequest>()
        val connectionMock = mock<HttpsURLConnection>()
        val inputStream = ByteArrayInputStream("1".toByteArray())

        doReturn(200).whenever(connectionMock).responseCode
        doReturn(inputStream).whenever(connectionMock).inputStream

        val response = requestSpy.readResponse(connectionMock)

        assertEquals("1", response)
    }

    @Test
    fun `Reads error stream when response contains an error code from HttpsURLConnection successfully`() {
        val requestSpy = spy<NetworkRequest>()
        val connectionMock = mock<HttpsURLConnection>()
        val errorStream = ByteArrayInputStream("Invalid params".toByteArray())

        doReturn(500).whenever(connectionMock).responseCode
        doReturn(errorStream).whenever(connectionMock).errorStream

        val response = requestSpy.readResponse(connectionMock)

        assertEquals("Invalid params", response)
    }
}

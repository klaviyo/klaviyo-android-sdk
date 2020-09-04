package com.klaviyo.coresdk.networking

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.test.filters.SdkSuppress
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.requests.NetworkRequest
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

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
        val requestSpy = spy<NetworkRequest>()

        doReturn(connectivityManagerMock).whenever(contextSpy).getSystemService(Context.CONNECTIVITY_SERVICE)
        whenever(connectivityManagerMock.activeNetworkInfo).thenReturn(networkInfoMock)
        whenever(networkInfoMock.isConnectedOrConnecting).thenReturn(true)

        val isConnected = requestSpy.isInternetConnected(contextSpy)

        assertEquals(true, isConnected)
    }

    @Test
    @SdkSuppress(minSdkVersion = 23)
    fun `Is internet connected on Android M or above given the application context`() {
        val contextSpy = spy<Context>()
        val connectivityManagerMock = mock<ConnectivityManager>()
        val networkInfoMock = mock<NetworkInfo>()
        val networkMock = mock<Network>()
        val networkCapabilitiesMock = mock<NetworkCapabilities>()
        val requestSpy = spy<NetworkRequest>()

        doReturn(connectivityManagerMock).whenever(contextSpy).getSystemService(Context.CONNECTIVITY_SERVICE)
        whenever(connectivityManagerMock.activeNetworkInfo).thenReturn(networkInfoMock)
        whenever(connectivityManagerMock.activeNetwork).thenReturn(networkMock)
        whenever(connectivityManagerMock.getNetworkCapabilities(networkMock)).thenReturn(networkCapabilitiesMock)
        whenever(networkCapabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true)
        whenever(networkInfoMock.isConnectedOrConnecting).thenReturn(true)

        val isConnected = requestSpy.isInternetConnected(contextSpy)

        assertEquals(true, isConnected)
    }

    @Test
    fun `Send network request with query information successfully`() {
        val jsonString = "{\"customer_properties\":{\"\$phone_number\":\"+12223334444\",\"\$email\":\"test@test.com\"},\"time\":\"200\",\"event\":\"test_event\",\"properties\":{\"custom_value\":\"200\"}}"
        val encodedData = "eyJjdXN0b21lcl9wcm9wZXJ0aWVzIjp7IiRwaG9uZV9udW1iZXIiOiIrMTIyMjMzMzQ0NDQiLCIkZW1haWwiOiJ0ZXN0QHRlc3QuY29tIn0sInRpbWUiOiIyMDAiLCJldmVudCI6InRlc3RfZXZlbnQiLCJwcm9wZXJ0aWVzIjp7ImN1c3RvbV92YWx1ZSI6IjIwMCJ9fQ=="

        val requestSpy = spy<NetworkRequest>()
        val urlMock = mock<URL>()
        val connectionMock = mock<HttpsURLConnection>()


        doReturn(urlMock).whenever(requestSpy).buildURL()
        doReturn(RequestMethod.GET).whenever(requestSpy).requestMethod
        doReturn(jsonString).whenever(requestSpy).queryData

        doReturn(true).whenever(requestSpy).isInternetConnected(any())
        whenever(urlMock.openConnection()).thenReturn(connectionMock)
        doReturn(encodedData).whenever(requestSpy).encodeToBase64(any())
        doReturn("1").whenever(requestSpy).readResponse(connectionMock)

        val response = requestSpy.sendNetworkRequest()

        assertEquals("1", response)
    }

    @Test
    fun `Send network request without query information successfully`() {
        val requestSpy = spy<NetworkRequest>()
        val urlMock = mock<URL>()
        val connectionMock = mock<HttpsURLConnection>()

        doReturn(urlMock).whenever(requestSpy).buildURL()
        doReturn(RequestMethod.GET).whenever(requestSpy).requestMethod

        doReturn(true).whenever(requestSpy).isInternetConnected(contextMock)
        whenever(urlMock.openConnection()).thenReturn(connectionMock)
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
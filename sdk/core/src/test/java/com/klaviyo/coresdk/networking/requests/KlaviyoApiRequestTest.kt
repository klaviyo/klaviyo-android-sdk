package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.BaseTest
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class KlaviyoApiRequestTest : BaseTest() {
    private val stubUrlPath = "test"
    private val stubBaseUrl = "https://valid.url"
    private val stubFullUrl = "$stubBaseUrl/$stubUrlPath"

    private val stubErrorResponse = "error"
    private val stubSuccessResponse = "success"
    private val bodySlot = slot<String>()

    private fun withConnectionMock(expectedUrl: URL): HttpURLConnection {
        val connectionMock = spyk(expectedUrl.openConnection()) as HttpURLConnection
        val inputStream = ByteArrayInputStream(stubSuccessResponse.toByteArray())
        val errorStream = ByteArrayInputStream(stubErrorResponse.toByteArray())

        mockkObject(HttpUtil)
        every { HttpUtil.openConnection(expectedUrl) } returns connectionMock
        every { HttpUtil.writeToConnection(capture(bodySlot), connectionMock) } returns Unit
        every { connectionMock.connect() } returns Unit
        every { connectionMock.inputStream } returns inputStream
        every { connectionMock.errorStream } returns errorStream

        return connectionMock
    }

    @Before
    override fun setup() {
        super.setup()

        every { networkMonitorMock.isNetworkConnected() } returns true
        every { configMock.networkTimeout } returns 1
        every { configMock.networkFlushInterval } returns 1
        every { configMock.baseUrl } returns stubBaseUrl
    }

    @Test
    fun `Builds url with empty query data`() {
        val request = KlaviyoApiRequest(stubUrlPath, RequestMethod.GET)

        val expectedUrl = URL(stubFullUrl)
        val actualUrl = request.url

        assertEquals(expectedUrl, actualUrl)
    }

    @Test
    fun `Builds url with headers`() {
        val connectionMock = withConnectionMock(URL(stubFullUrl))
        val request = KlaviyoApiRequest(stubUrlPath, RequestMethod.GET).apply {
            headers = mapOf("header" to "value")
        }
        request.send()

        verify { connectionMock.setRequestProperty("header", "value") }
    }

    @Test
    fun `Builds url with multiple query parameters`() {
        val request = KlaviyoApiRequest(stubUrlPath, RequestMethod.GET).apply {
            query = mapOf("first" to "second", "query" to "1", "flag" to "false")
        }

        val expectedUrl = URL("$stubFullUrl?first=second&query=1&flag=false")
        val actualUrl = request.url

        assertEquals(expectedUrl, actualUrl)
    }

    @Test
    fun `Opening HTTP connection tests for SSL protocol`() {
        val url = URL(stubFullUrl)
        assert(HttpUtil.openConnection(url) is HttpsURLConnection)
    }

    @Test
    fun `Opening HTTP Connection is case insensitive`() {
        HttpUtil.openConnection(URL("HTTP://valid.url"))
    }

    @Test(expected = IOException::class)
    fun `Opening HTTP Connection detects bad protocols`() {
        HttpUtil.openConnection(URL("FTP://valid.url"))
    }

    @Test
    fun `Send returns null when internet is unavailable`() {
        every { networkMonitorMock.isNetworkConnected() } returns false

        val request = KlaviyoApiRequest(stubUrlPath, RequestMethod.GET)
        val actualResponse = request.send()

        assertEquals(null, actualResponse)
    }

    @Test
    fun `Successful GET request returns success payload`() {
        val connectionMock = withConnectionMock(URL(stubFullUrl))
        every { connectionMock.responseCode } returns 200

        val request = KlaviyoApiRequest(stubUrlPath, RequestMethod.GET)
        val actualResponse = request.send()

        verify { connectionMock.connect() }
        verify { connectionMock.disconnect() }
        assertEquals(stubSuccessResponse, actualResponse)
    }

    @Test
    fun `Failed GET request returns error payload`() {
        val connectionMock = withConnectionMock(URL(stubFullUrl))
        every { connectionMock.responseCode } returns 400

        val request = KlaviyoApiRequest(stubUrlPath, RequestMethod.GET)
        val actualResponse = request.send()

        assertEquals(stubErrorResponse, actualResponse)
    }

    @Test
    fun `Successful POST with body`() {
        val connectionMock = withConnectionMock(URL(stubFullUrl))
        val expectedBody = "JSON"

        every { connectionMock.responseCode } returns 200

        val request = KlaviyoApiRequest(stubUrlPath, RequestMethod.POST).apply {
            body = expectedBody
        }
        val actualResponse = request.send()

        assert(bodySlot.isCaptured)
        assertEquals(bodySlot.captured, expectedBody)
        verify { connectionMock.connect() }
        verify { connectionMock.disconnect() }
        assertEquals(stubSuccessResponse, actualResponse)
    }

    @Test
    fun `Successful POST without body`() {
        val connectionMock = withConnectionMock(URL(stubFullUrl))

        every { connectionMock.responseCode } returns 200

        val request = KlaviyoApiRequest(stubUrlPath, RequestMethod.POST)
        val actualResponse = request.send()

        assert(!bodySlot.isCaptured)
        verify { connectionMock.connect() }
        verify { connectionMock.disconnect() }
        assertEquals(stubSuccessResponse, actualResponse)
    }

    override fun clear() {
        super.clear()
        unmockkObject(HttpUtil)
    }
}

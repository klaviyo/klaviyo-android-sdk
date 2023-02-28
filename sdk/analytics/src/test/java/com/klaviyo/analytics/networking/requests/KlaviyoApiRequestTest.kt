package com.klaviyo.analytics.networking.requests

import com.klaviyo.core.config.KlaviyoConfig
import com.klaviyo.core_shared_tests.BaseTest
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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

internal class KlaviyoApiRequestTest : BaseTest() {
    private val stubUrlPath = "test"
    private val stubFullUrl = "${configMock.baseUrl}/$stubUrlPath"

    private val stubErrorResponse = "error"
    private val stubSuccessResponse = "success"
    private val bodySlot = slot<String>()

    private fun withConnectionMock(expectedUrl: URL): HttpURLConnection {
        val connectionSpy = spyk(expectedUrl.openConnection()) as HttpURLConnection
        val inputStream = ByteArrayInputStream(stubSuccessResponse.toByteArray())
        val errorStream = ByteArrayInputStream(stubErrorResponse.toByteArray())

        mockkObject(HttpUtil)
        every { HttpUtil.openConnection(expectedUrl) } returns connectionSpy
        every { HttpUtil.writeToConnection(capture(bodySlot), connectionSpy) } returns Unit
        every { connectionSpy.connect() } returns Unit
        every { connectionSpy.inputStream } returns inputStream
        every { connectionSpy.errorStream } returns errorStream

        return connectionSpy
    }

    @Before
    override fun setup() {
        super.setup()

        every { networkMonitorMock.isNetworkConnected() } returns true
        every { configMock.networkTimeout } returns 1
        every { configMock.networkFlushIntervals } returns intArrayOf(10_000, 30_000, 60_000)
    }

    @Test
    fun `Treats same UUID as equal requests for deduplication`() {
        val request1 = KlaviyoApiRequest(stubUrlPath, RequestMethod.GET, "", "uuid1")
        val request2 = KlaviyoApiRequest(stubUrlPath, RequestMethod.GET, "", "uuid2")
        val request3 = KlaviyoApiRequest(stubUrlPath, RequestMethod.GET, "", "uuid2")

        assertNotEquals(request1, null)
        assertNotEquals(request1, request2)
        assertEquals(request2, request2)
        assertEquals(request2, request3)
        assertEquals(request2, request3)
        assert(listOf(request1, request2).contains(request3))
        assertEquals(request2.hashCode(), request3.hashCode())
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
    fun `Send returns unsent status when internet is unavailable`() {
        every { networkMonitorMock.isNetworkConnected() } returns false

        val request = KlaviyoApiRequest(stubUrlPath, RequestMethod.GET)

        assertEquals(KlaviyoApiRequest.Status.Unsent, request.send())
    }

    @Test
    fun `Successful GET request returns success status`() {
        val connectionMock = withConnectionMock(URL(stubFullUrl))
        every { connectionMock.responseCode } returns 200

        val request = KlaviyoApiRequest(stubUrlPath, RequestMethod.GET)
        val actualResponse = request.send()

        verify { connectionMock.connect() }
        verify { connectionMock.disconnect() }
        assertEquals(KlaviyoApiRequest.Status.Complete, actualResponse)
    }

    @Test
    fun `Failed GET request returns failure status`() {
        val connectionMock = withConnectionMock(URL(stubFullUrl))
        every { connectionMock.responseCode } returns 500

        val request = KlaviyoApiRequest(stubUrlPath, RequestMethod.GET)

        assertEquals(KlaviyoApiRequest.Status.Failed, request.send())
    }

    @Test
    fun `Rate limited request returns retryable status and fails after max attempts`() {
        val connectionMock = withConnectionMock(URL(stubFullUrl))
        every { connectionMock.responseCode } returns 429

        val request = KlaviyoApiRequest(stubUrlPath, RequestMethod.GET)

        repeat(configMock.networkMaxRetries) {
            // Should be retryable until max attempts hit
            assertEquals(KlaviyoApiRequest.Status.PendingRetry, request.send())
        }

        // Final attempt should return fail
        assertEquals(KlaviyoApiRequest.Status.Failed, request.send())
        assertEquals(configMock.networkMaxRetries + 1, request.attempts)
    }

    @Test
    fun `Successful POST with body`() {
        val connectionMock = withConnectionMock(URL(stubFullUrl))
        val expectedBody = JSONObject("{\"test\":1}")

        every { connectionMock.responseCode } returns 200

        val request = KlaviyoApiRequest(stubUrlPath, RequestMethod.POST).apply {
            body = expectedBody
        }
        val actualResponse = request.send()

        assert(bodySlot.isCaptured)
        assertEquals("{\"data\":{\"test\":1}}", bodySlot.captured)
        verify { connectionMock.connect() }
        verify { connectionMock.disconnect() }
        assertEquals(KlaviyoApiRequest.Status.Complete, actualResponse)
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
        assertEquals(KlaviyoApiRequest.Status.Complete, actualResponse)
    }

    private val postJson =
        "{\"headers\":{\"headerKey\":\"headerValue\"},\"method\":\"POST\",\"query\":{\"queryKey\":\"queryValue\"},\"time\":\"time\",\"body\":{\"bodyKey\":\"bodyValue\"},\"uuid\":\"uuid\",\"url_path\":\"test\"}"
    private val getJson =
        "{\"headers\":{\"headerKey\":\"headerValue\"},\"method\":\"GET\",\"query\":{\"queryKey\":\"queryValue\"},\"time\":\"time\",\"uuid\":\"uuid\",\"url_path\":\"test\"}"

    @Test
    fun `Serializes POST to JSON`() {
        val post = KlaviyoApiRequest(
            "test",
            RequestMethod.POST,
            "time",
            "uuid"
        ).apply {
            headers = mapOf("headerKey" to "headerValue")
            query = mapOf("queryKey" to "queryValue")
            body = JSONObject(mapOf("bodyKey" to "bodyValue"))
        }

        val actualJson = post.toJson()

        assertEquals(postJson, actualJson)
    }

    @Test
    fun `Serializes GET to JSON`() {
        val post = KlaviyoApiRequest(
            "test",
            RequestMethod.GET,
            "time",
            "uuid"
        ).apply {
            headers = mapOf("headerKey" to "headerValue")
            query = mapOf("queryKey" to "queryValue")
        }

        val actualJson = post.toJson()

        assertEquals(getJson, actualJson)
    }

    @Test
    fun `Deserializes POST from JSON`() {
        val post = KlaviyoApiRequest.fromJson(JSONObject(postJson))

        assertEquals("test", post.urlPath)
        assertEquals(RequestMethod.POST, post.method)
        assertEquals("time", post.time)
        assertEquals("uuid", post.uuid)
        assert(post.headers.count() == 1)
        assertEquals("headerValue", post.headers["headerKey"])
        assert(post.query.count() == 1)
        assertEquals("queryValue", post.query["queryKey"])
        assert(post.body != null)
        assertEquals("{\"bodyKey\":\"bodyValue\"}", post.body.toString())
    }

    @Test
    fun `Deserializes GET from JSON`() {
        val get = KlaviyoApiRequest.fromJson(JSONObject(getJson))

        assertEquals("test", get.urlPath)
        assertEquals(RequestMethod.GET, get.method)
        assertEquals("time", get.time)
        assertEquals("uuid", get.uuid)
        assert(get.headers.count() == 1)
        assertEquals("headerValue", get.headers["headerKey"])
        assert(get.query.count() == 1)
        assertEquals("queryValue", get.query["queryKey"])
        assertEquals(null, get.body)
    }

    override fun clear() {
        super.clear()
        unmockkObject(HttpUtil)
    }
}

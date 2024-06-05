package com.klaviyo.analytics.networking.requests

import com.klaviyo.core.networking.NetworkMonitor
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class KlaviyoApiRequestTest : BaseApiRequestTest<KlaviyoApiRequest>() {

    override val expectedUrl = "test"

    override val expectedMethod: RequestMethod = RequestMethod.GET

    override val expectedQuery: Map<String, String> = emptyMap()

    private val expectedFullUrl = "${configMock.baseUrl}/$expectedUrl"

    private val bodySlot = slot<String>()

    private fun withConnectionMock(expectedUrl: URL): HttpURLConnection {
        val connectionSpy = spyk(expectedUrl.openConnection()) as HttpURLConnection
        val inputStream = ByteArrayInputStream("success".toByteArray())
        val errorStream = ByteArrayInputStream("error".toByteArray())

        mockkObject(HttpUtil)
        every { HttpUtil.openConnection(expectedUrl) } returns connectionSpy
        every { HttpUtil.writeToConnection(capture(bodySlot), connectionSpy) } returns Unit
        every { connectionSpy.connect() } returns Unit
        every { connectionSpy.responseCode } returns 202
        every { connectionSpy.headerFields } returns emptyMap()
        every { connectionSpy.inputStream } returns inputStream
        every { connectionSpy.errorStream } returns errorStream

        return connectionSpy
    }

    @Before
    override fun setup() {
        super.setup()
        every { networkMonitorMock.isNetworkConnected() } returns true
        every { configMock.networkTimeout } returns 1
        every { configMock.networkFlushIntervals } returns longArrayOf(10_000L, 30_000L, 60_000L)
    }

    @After
    override fun cleanup() {
        super.cleanup()
        unmockkObject(HttpUtil)
    }

    override fun makeTestRequest(): KlaviyoApiRequest =
        KlaviyoApiRequest(expectedUrl, RequestMethod.GET)

    @Test
    fun `Treats same UUID as equal requests for deduplication`() {
        val request1 = KlaviyoApiRequest(expectedUrl, RequestMethod.GET, uuid = "uuid1")
        val request2 = KlaviyoApiRequest(expectedUrl, RequestMethod.GET, uuid = "uuid2")
        val request3 = KlaviyoApiRequest(expectedUrl, RequestMethod.GET, uuid = "uuid2")

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
        val request = makeTestRequest()

        val expectedUrl = URL(expectedFullUrl)
        val actualUrl = request.url

        assertEquals(expectedUrl, actualUrl)
    }

    @Test
    fun `Builds url with headers`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        val request = makeTestRequest().apply {
            headers.replaceAllWith(mapOf("header" to "value"))
        }
        request.send()

        verify { connectionMock.setRequestProperty("header", "value") }
    }

    @Test
    fun `Invokes pre-send callback when inflight`() {
        withConnectionMock(URL(expectedFullUrl))
        val request = makeTestRequest()

        var called = false
        request.send {
            called = true
            assertEquals(KlaviyoApiRequest.Status.Inflight.name, request.state)
        }

        assert(called)
    }

    @Test
    fun `Sets send times on send`() {
        withConnectionMock(URL(expectedFullUrl))
        val request = makeTestRequest()

        assertNull(request.startTime)
        assertNull(request.endTime)
        request.send()
        assertEquals(TIME, request.startTime)
        assertEquals(TIME, request.endTime)
    }

    @Test
    fun `Increments attempt counter on send and uses correct attempt number in header`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        val request = makeTestRequest()

        assertEquals(0, request.attempts)
        request.send()
        assertEquals(1, request.attempts)
        assertEquals(request.headers["X-Klaviyo-Attempt-Count"], "1/50")
        verify { connectionMock.setRequestProperty("X-Klaviyo-Attempt-Count", "1/50") }
    }

    @Test
    fun `Sets response headers after send`() {
        val expectedHeaders = mapOf("Response-Header" to listOf("1", "2", "3"))
        withConnectionMock(URL(expectedFullUrl)).also {
            every { it.headerFields } returns expectedHeaders
        }

        val request = makeTestRequest()
        request.send()
        assertEquals(expectedHeaders, request.responseHeaders)
    }

    @Test
    fun `Parses Retry-After header if present and adds jitter`() {
        val expectedHeaders = mapOf("Retry-After" to listOf("25"))
        every { configMock.networkJitterRange } returns 1..1
        withConnectionMock(URL(expectedFullUrl)).also {
            every { it.headerFields } returns expectedHeaders
        }

        val request = makeTestRequest()
        request.send()
        assertEquals(26_000L, request.computeRetryInterval())
    }

    @Test
    fun `Falls back on network interval without jitter when Retry-After header is missing or invalid`() {
        // Wifi interval is 10s, force jitter to be 1s
        every { networkMonitorMock.getNetworkType() } returns NetworkMonitor.NetworkType.Wifi
        every { configMock.networkJitterRange } returns 1..1

        val request = makeTestRequest()
        assertEquals(10_000L, request.computeRetryInterval())

        withConnectionMock(URL(expectedFullUrl)).also {
            every { it.headerFields } returns mapOf("Retry-After" to listOf("invalid time"))
            request.send()
            assertEquals(10_000L, request.computeRetryInterval())
        }

        withConnectionMock(URL(expectedFullUrl)).also {
            every { it.headerFields } returns emptyMap()
            request.send()
            assertEquals(10_000L, request.computeRetryInterval())
        }

        assertEquals(2, request.attempts)
    }

    @Test
    fun `Builds url with multiple query parameters`() {
        val request = makeTestRequest().apply {
            query = mapOf("first" to "second", "query" to "1", "flag" to "false")
        }

        val expectedUrl = URL("$expectedFullUrl?first=second&query=1&flag=false")
        val actualUrl = request.url

        assertEquals(expectedUrl, actualUrl)
    }

    @Test
    fun `Opening HTTP connection tests for SSL protocol`() {
        val url = URL(expectedFullUrl)
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

        val request = makeTestRequest()

        assertEquals(KlaviyoApiRequest.Status.Unsent, request.send())
    }

    @Test
    fun `Successful GET request returns success status`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.responseCode } returns 200

        val request = makeTestRequest()
        val actualResponse = request.send()

        verify { connectionMock.connect() }
        verify { connectionMock.disconnect() }
        assertEquals(KlaviyoApiRequest.Status.Complete, actualResponse)
    }

    @Test
    fun `Failed GET request returns failure status`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.responseCode } returns 500

        val request = makeTestRequest()

        assertEquals(KlaviyoApiRequest.Status.Failed, request.send())
    }

    @Test
    fun `Rate limited request returns retryable status and fails after max attempts`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.responseCode } returns 429

        val request = makeTestRequest()

        repeat(configMock.networkMaxAttempts - 1) {
            // Should be retryable until max attempts hit
            assertEquals(KlaviyoApiRequest.Status.PendingRetry, request.send())
            assertEquals(request.headers["X-Klaviyo-Attempt-Count"], "${it + 1}/50")
        }

        // Final attempt hitting a 429 should result in Failed status
        assertEquals(KlaviyoApiRequest.Status.Failed, request.send())
        assertEquals(50, request.attempts)
        assertEquals(request.headers["X-Klaviyo-Attempt-Count"], "50/50")
    }

    @Test
    fun `Successful POST with body`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        val stubBody = "{\"test\":1}"
        val expectedBody = JSONObject(stubBody)

        every { connectionMock.responseCode } returns 200

        val request = KlaviyoApiRequest(expectedUrl, RequestMethod.POST).apply {
            body = expectedBody
        }
        val actualResponse = request.send()

        assert(bodySlot.isCaptured)
        assertEquals(stubBody, bodySlot.captured)
        verify { connectionMock.connect() }
        verify { connectionMock.disconnect() }
        assertEquals(KlaviyoApiRequest.Status.Complete, actualResponse)
    }

    @Test
    fun `Successful POST without body`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))

        every { connectionMock.responseCode } returns 200

        val request = KlaviyoApiRequest(expectedUrl, RequestMethod.POST)
        val actualResponse = request.send()

        assert(!bodySlot.isCaptured)
        verify { connectionMock.connect() }
        verify { connectionMock.disconnect() }
        assertEquals(KlaviyoApiRequest.Status.Complete, actualResponse)
    }

    private val postJson = """
        {
          "request_type": "KlaviyoApiRequest",
          "headers": {
            "headerKey": "headerValue"
          },
          "method": "POST",
          "query": {
            "queryKey": "queryValue"
          },
          "time": $TIME,
          "body": {
            "bodyKey": "bodyValue"
          },
          "uuid": "uuid",
          "url_path": "test"
        }
    """.trimIndent()

    private val getJson = """
        {
          "request_type": "KlaviyoApiRequest",
          "headers": {
            "headerKey": "headerValue"
          },
          "method": "GET",
          "query": {
            "queryKey": "queryValue"
          },
          "time": $TIME,
          "uuid": "uuid",
          "url_path": "test"
        }
    """.trimIndent()

    @Test
    fun `Serializes POST to JSON`() {
        val post = KlaviyoApiRequest(
            "test",
            RequestMethod.POST,
            uuid = "uuid"
        ).apply {
            headers.replaceAllWith(mapOf("headerKey" to "headerValue"))
            query = mapOf("queryKey" to "queryValue")
            body = JSONObject(mapOf("bodyKey" to "bodyValue"))
        }

        compareJson(JSONObject(postJson), post.toJson())
    }

    @Test
    fun `Serializes GET to JSON`() {
        val post = KlaviyoApiRequest(
            "test",
            RequestMethod.GET,
            uuid = "uuid"
        ).apply {
            headers.replaceAllWith(mapOf("headerKey" to "headerValue"))
            query = mapOf("queryKey" to "queryValue")
        }

        compareJson(JSONObject(getJson), post.toJson())
    }

    @Test
    fun `Deserializes POST from JSON`() {
        val post = KlaviyoApiRequestDecoder.fromJson(JSONObject(postJson))

        assertEquals("test", post.urlPath)
        assertEquals(RequestMethod.POST, post.method)
        assertEquals(TIME, post.queuedTime)
        assertEquals("uuid", post.uuid)
        assert(post.headers.count() == 1)
        assertEquals("headerValue", post.headers["headerKey"])
        assert(post.query.count() == 1)
        assertEquals("queryValue", post.query["queryKey"])
        assert(post.body != null)
        assertEquals("{\"bodyKey\":\"bodyValue\"}", post.body.toString())
        assertEquals(0, post.attempts)
    }

    @Test
    fun `Deserializes GET from JSON`() {
        val get = KlaviyoApiRequestDecoder.fromJson(JSONObject(getJson))

        assertEquals("test", get.urlPath)
        assertEquals(RequestMethod.GET, get.method)
        assertEquals(TIME, get.queuedTime)
        assertEquals("uuid", get.uuid)
        assert(get.headers.count() == 1)
        assertEquals("headerValue", get.headers["headerKey"])
        assert(get.query.count() == 1)
        assertEquals("queryValue", get.query["queryKey"])
        assertEquals(null, get.body)
        assertEquals(0, get.attempts)
    }
}

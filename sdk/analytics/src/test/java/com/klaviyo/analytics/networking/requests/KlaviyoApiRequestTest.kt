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
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class KlaviyoApiRequestTest : BaseApiRequestTest<KlaviyoApiRequest>() {

    override val expectedPath = "test"

    override val expectedUrl: URL = URL("${mockConfig.baseUrl}/$expectedPath")

    override val expectedMethod: RequestMethod = RequestMethod.GET

    override val expectedQuery: Map<String, String> = emptyMap()

    private val expectedFullUrl = "${mockConfig.baseUrl}/$expectedPath"

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

    private fun withErrorConnectionMock(expectedUrl: URL, errorBodyString: String): HttpURLConnection {
        val connectionSpy = spyk(expectedUrl.openConnection()) as HttpURLConnection
        val inputStream = ByteArrayInputStream("success".toByteArray())
        val errorStream = ByteArrayInputStream(errorBodyString.toByteArray())

        mockkObject(HttpUtil)
        every { HttpUtil.openConnection(expectedUrl) } returns connectionSpy
        every { HttpUtil.writeToConnection(capture(bodySlot), connectionSpy) } returns Unit
        every { connectionSpy.connect() } returns Unit
        every { connectionSpy.responseCode } returns 400
        every { connectionSpy.headerFields } returns emptyMap()
        every { connectionSpy.inputStream } returns inputStream
        every { connectionSpy.errorStream } returns errorStream

        return connectionSpy
    }

    @Before
    override fun setup() {
        super.setup()
        every { mockNetworkMonitor.isNetworkConnected() } returns true
        every { mockConfig.networkTimeout } returns 1
        every { mockConfig.networkFlushIntervals } returns longArrayOf(10_000L, 30_000L, 60_000L)
    }

    @After
    override fun cleanup() {
        super.cleanup()
        unmockkObject(HttpUtil)
    }

    override fun makeTestRequest(): KlaviyoApiRequest =
        KlaviyoApiRequest(expectedPath, RequestMethod.GET)

    @Test
    fun `Treats same UUID as equal requests for deduplication`() {
        val request1 = KlaviyoApiRequest(expectedPath, RequestMethod.GET, uuid = "uuid1")
        val request2 = KlaviyoApiRequest(expectedPath, RequestMethod.GET, uuid = "uuid2")
        val request3 = KlaviyoApiRequest(expectedPath, RequestMethod.GET, uuid = "uuid2")

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
    fun `Invokes callback on status changes for success`() {
        withConnectionMock(URL(expectedFullUrl))
        val request = makeTestRequest()

        var callCount = 0
        request.send {
            callCount++
            when (callCount) {
                1 -> assertEquals(KlaviyoApiRequest.Status.Inflight.name, request.state)
                2 -> assertEquals(KlaviyoApiRequest.Status.Complete.name, request.state)
                else -> throw AssertionError("Callback called too many times")
            }
        }

        assertEquals(2, callCount)
    }

    @Test
    fun `Invokes callback on status change for failure`() {
        withErrorConnectionMock(URL(expectedFullUrl), "")
        val request = makeTestRequest()

        var callCount = 0
        request.send {
            callCount++
            when (callCount) {
                1 -> assertEquals(KlaviyoApiRequest.Status.Inflight.name, request.state)
                2 -> assertEquals(KlaviyoApiRequest.Status.Failed.name, request.state)
                else -> throw AssertionError("Callback called too many times")
            }
        }

        assertEquals(2, callCount)
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
        every { mockConfig.networkJitterRange } returns 1..1
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
        every { mockNetworkMonitor.getNetworkType() } returns NetworkMonitor.NetworkType.Wifi
        every { mockConfig.networkJitterRange } returns 1..1

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
        every { mockNetworkMonitor.isNetworkConnected() } returns false

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
    fun `Failed GET request with 4xx returns failure status`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.responseCode } returns 400

        val request = makeTestRequest()

        assertEquals(KlaviyoApiRequest.Status.Failed, request.send())
    }

    @Test
    fun `Rate limited request returns retryable status and fails after max attempts`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.responseCode } returns 429

        val request = makeTestRequest()

        repeat(mockConfig.networkMaxAttempts - 1) {
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

        val request = KlaviyoApiRequest(expectedPath, RequestMethod.POST).apply {
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

        val request = KlaviyoApiRequest(expectedPath, RequestMethod.POST)
        val actualResponse = request.send()

        assert(!bodySlot.isCaptured)
        verify { connectionMock.connect() }
        verify { connectionMock.disconnect() }
        assertEquals(KlaviyoApiRequest.Status.Complete, actualResponse)
    }

    private val postJson = """
        {
          "request_type": "KlaviyoApiRequest",
          "base_url": "https://test.fake-klaviyo.com",
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
          "base_url": "https://test.fake-klaviyo.com",
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

    @Test
    fun `Malformed error response body`() {
        withErrorConnectionMock(
            URL(expectedFullUrl),
            errorBodyString = """
                    {
                    ckajns dlckjabsdlckjbsdcsc
                    kjdfns vkajn df
                    8723986243
                    all crabs are crustaceans
            """.trimIndent()
        )
        val expectedErrorBody = KlaviyoErrorResponse(listOf())
        val request = makeTestRequest()
        request.send()

        assertEquals(request.errorBody, expectedErrorBody)
    }

    @Test
    fun `Empty error response body`() {
        withErrorConnectionMock(
            URL(expectedFullUrl),
            errorBodyString = """
                    {
                    }
            """.trimIndent()
        )
        val expectedErrorBody = KlaviyoErrorResponse(listOf())
        val request = makeTestRequest()
        request.send()

        assertEquals(request.errorBody, expectedErrorBody)
    }

    @Test
    fun `Phone number format error body created`() {
        withErrorConnectionMock(
            URL(expectedFullUrl),
            errorBodyString = """
                    {
                      "errors": [
                        {
                          "id": "67ed6dbf-1653-499b-a11d-30310aa01ff7",
                          "status": 400,
                          "code": "invalid",
                          "title": "Invalid input.",
                          "detail": "Invalid phone number format (Example of a valid format: +12345678901)",
                          "source": {
                            "pointer": "/data/attributes/phone_number"
                          },
                          "links": {},
                          "meta": {}
                        }
                      ]
                    }
            """.trimIndent()
        )
        val expectedErrorBody = KlaviyoErrorResponse(
            listOf(
                KlaviyoError(
                    id = "67ed6dbf-1653-499b-a11d-30310aa01ff7",
                    status = 400,
                    title = "Invalid input.",
                    detail = "Invalid phone number format (Example of a valid format: +12345678901)",
                    source = KlaviyoErrorSource(
                        pointer = "/data/attributes/phone_number"
                    )
                )
            )
        )
        val request = makeTestRequest()
        request.send()

        assertEquals(request.errorBody, expectedErrorBody)
    }

    @Test
    fun `Email format error body created`() {
        withErrorConnectionMock(
            URL(expectedFullUrl),
            errorBodyString = """
                    {
                      "errors": [
                        {
                          "id": "4f739784-390b-4df3-acd8-6eb07d60e6b4",
                          "status": 400,
                          "code": "invalid",
                          "title": "Invalid input.",
                          "detail": "Invalid email address",
                          "source": {
                            "pointer": "/data/attributes/email"
                          },
                          "links": {},
                          "meta": {}
                        }
                      ]
                    }
            """.trimIndent()
        )
        val expectedErrorBody = KlaviyoErrorResponse(
            listOf(
                KlaviyoError(
                    id = "4f739784-390b-4df3-acd8-6eb07d60e6b4",
                    status = 400,
                    title = "Invalid input.",
                    detail = "Invalid email address",
                    source = KlaviyoErrorSource(
                        pointer = "/data/attributes/email"
                    )
                )
            )
        )
        val request = makeTestRequest()
        request.send()

        assertEquals(request.errorBody, expectedErrorBody)
    }

    @Test
    fun `SocketTimeoutException returns PendingRetry when under max attempts`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.connect() } throws SocketTimeoutException("Connection timed out")

        val request = makeTestRequest()
        assertEquals(KlaviyoApiRequest.Status.PendingRetry, request.send())
        assertEquals(1, request.attempts)
    }

    @Test
    fun `SocketException returns PendingRetry when under max attempts`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.connect() } throws SocketException("Network is unreachable")

        val request = makeTestRequest()
        assertEquals(KlaviyoApiRequest.Status.PendingRetry, request.send())
        assertEquals(1, request.attempts)
    }

    @Test
    fun `UnknownHostException returns PendingRetry when under max attempts`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.connect() } throws UnknownHostException("Unable to resolve host")

        val request = makeTestRequest()
        assertEquals(KlaviyoApiRequest.Status.PendingRetry, request.send())
        assertEquals(1, request.attempts)
    }

    @Test
    fun `IOException with network keyword returns PendingRetry when under max attempts`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.connect() } throws IOException("Network error occurred")

        val request = makeTestRequest()
        assertEquals(KlaviyoApiRequest.Status.PendingRetry, request.send())
        assertEquals(1, request.attempts)
    }

    @Test
    fun `Network IOException after max attempts returns Failed`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.connect() } throws SocketTimeoutException("Connection timed out")

        val request = makeTestRequest()

        repeat(mockConfig.networkMaxAttempts - 1) {
            assertEquals(KlaviyoApiRequest.Status.PendingRetry, request.send())
        }

        // Final attempt should return Failed
        assertEquals(KlaviyoApiRequest.Status.Failed, request.send())
        assertEquals(mockConfig.networkMaxAttempts, request.attempts)
    }

    @Test
    fun `Non-network IOException returns Failed immediately`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.connect() } throws IOException("File not found")

        val request = makeTestRequest()
        assertEquals(KlaviyoApiRequest.Status.Failed, request.send())
        assertEquals(1, request.attempts)
    }

    @Test
    fun `500 response code returns PendingRetry when under max attempts`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.responseCode } returns 500

        val request = makeTestRequest()
        assertEquals(KlaviyoApiRequest.Status.PendingRetry, request.send())
        assertEquals(1, request.attempts)
    }

    @Test
    fun `502 response code returns PendingRetry when under max attempts`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.responseCode } returns 502

        val request = makeTestRequest()
        assertEquals(KlaviyoApiRequest.Status.PendingRetry, request.send())
        assertEquals(1, request.attempts)
    }

    @Test
    fun `503 response code returns PendingRetry when under max attempts`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.responseCode } returns 503

        val request = makeTestRequest()
        assertEquals(KlaviyoApiRequest.Status.PendingRetry, request.send())
        assertEquals(1, request.attempts)
    }

    @Test
    fun `504 response code returns PendingRetry when under max attempts`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.responseCode } returns 504

        val request = makeTestRequest()
        assertEquals(KlaviyoApiRequest.Status.PendingRetry, request.send())
        assertEquals(1, request.attempts)
    }

    @Test
    fun `5xx response code after max attempts returns Failed`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.responseCode } returns 500

        val request = makeTestRequest()

        repeat(mockConfig.networkMaxAttempts - 1) {
            assertEquals(KlaviyoApiRequest.Status.PendingRetry, request.send())
        }

        // Final attempt should return Failed
        assertEquals(KlaviyoApiRequest.Status.Failed, request.send())
        assertEquals(mockConfig.networkMaxAttempts, request.attempts)
    }

    @Test
    fun `400 response code returns Failed immediately`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.responseCode } returns 400

        val request = makeTestRequest()
        assertEquals(KlaviyoApiRequest.Status.Failed, request.send())
        assertEquals(1, request.attempts)
    }

    @Test
    fun `401 response code returns Failed immediately`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.responseCode } returns 401

        val request = makeTestRequest()
        assertEquals(KlaviyoApiRequest.Status.Failed, request.send())
        assertEquals(1, request.attempts)
    }

    @Test
    fun `403 response code returns Failed immediately`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.responseCode } returns 403

        val request = makeTestRequest()
        assertEquals(KlaviyoApiRequest.Status.Failed, request.send())
        assertEquals(1, request.attempts)
    }

    @Test
    fun `404 response code returns Failed immediately`() {
        val connectionMock = withConnectionMock(URL(expectedFullUrl))
        every { connectionMock.responseCode } returns 404

        val request = makeTestRequest()
        assertEquals(KlaviyoApiRequest.Status.Failed, request.send())
        assertEquals(1, request.attempts)
    }
}

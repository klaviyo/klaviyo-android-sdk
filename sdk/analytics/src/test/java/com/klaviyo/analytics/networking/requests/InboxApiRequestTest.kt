package com.klaviyo.analytics.networking.requests

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.IOException
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class InboxApiRequestTest : BaseApiRequestTest<InboxApiRequest>() {

    private companion object {
        const val TOKEN = "secret-token-value"
        const val PROFILE_ID = "profile-1"
        const val PAGE_SIZE = 50
        const val POST_BODY = """{"data":{"type":"inbox-message-state-change"}}"""
        const val MEDIA_TYPE = "application/vnd.api+json"
    }

    override val expectedPath = "api/inbox-messages"

    override val expectedMethod = RequestMethod.GET

    override val expectedQuery = mapOf(
        "profile_id" to PROFILE_ID,
        "platform" to "android",
        "page[size]" to "$PAGE_SIZE"
    )

    override val expectedUrl: URL
        get() = URL(
            "${mockConfig.baseUrl}/$expectedPath?profile_id=$PROFILE_ID&platform=android&page[size]=$PAGE_SIZE"
        )

    override val expectedHeaders = mapOf(
        "Accept" to MEDIA_TYPE,
        "Revision" to "2027-01-15.pre",
        "User-Agent" to "Mock User Agent",
        "X-Klaviyo-Mobile" to "1",
        "X-Klaviyo-Attempt-Count" to "0/50",
        "X-Klaviyo-API-Test" to "true"
    )

    override fun makeTestRequest(): InboxApiRequest =
        InboxApiRequest.fetchPage(TOKEN, PROFILE_ID, null, PAGE_SIZE)

    private fun makePostRequest() = InboxApiRequest.submitChanges(TOKEN, POST_BODY)

    /**
     * Inbox requests are sent directly and never persisted, so there is no revive path to verify
     */
    @Test
    override fun `Uses expected URL after encoding and decoding even if base url has changed`() = Unit

    private val capturedBody = mutableListOf<String>()

    private fun withConnectionMock(
        code: Int = 200,
        body: String = "{}",
        headerFields: Map<String?, List<String>> = emptyMap()
    ) = mockInboxConnection(mockConfig.baseUrl, code, body, headerFields, capturedBody)

    @Before
    override fun setup() {
        super.setup()
        every { mockNetworkMonitor.isNetworkConnected() } returns true
    }

    @After
    override fun cleanup() {
        super.cleanup()
        unmockkObject(HttpUtil)
    }

    @Test
    fun `Token is absent from headers, JSON and string representations`() {
        listOf(makeTestRequest(), makePostRequest()).forEach { request ->
            assertFalse(request.headers.values.any { it.contains(TOKEN) })
            assertFalse(request.headers.keys.any { it.equals("Authorization", true) })
            assertFalse(request.toJson().toString().contains(TOKEN))
            assertFalse(request.toString().contains(TOKEN))
        }
    }

    @Test
    fun `Token is sent as a bearer and redirects and caching are disabled`() {
        val connection = withConnectionMock()

        makeTestRequest().send()

        // The JDK hides Authorization from getRequestProperty, so verify the setter call instead
        verify { connection.setRequestProperty("Authorization", "Bearer $TOKEN") }
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
    }

    @Test
    fun `Redirects and caching are left alone when there are no unlogged headers`() {
        val connection = withConnectionMock()

        KlaviyoApiRequest("test", RequestMethod.GET).send()

        assertTrue(connection.instanceFollowRedirects)
        assertTrue(connection.useCaches)
        verify(exactly = 0) { connection.setRequestProperty("Authorization", any()) }
    }

    @Test
    fun `GET sends accept and no content type on the wire`() {
        val connection = withConnectionMock()

        makeTestRequest().send()

        assertEquals(MEDIA_TYPE, connection.getRequestProperty("Accept"))
        assertEquals("2027-01-15.pre", connection.getRequestProperty("Revision"))
        assertEquals("true", connection.getRequestProperty("X-Klaviyo-API-Test"))
        assertNull(connection.getRequestProperty("Content-Type"))
    }

    @Test
    fun `POST sends the given body with the JSON API content type`() {
        val connection = withConnectionMock()
        val request = makePostRequest()

        request.send()

        assertEquals(RequestMethod.POST, request.method)
        assertEquals(
            "${mockConfig.baseUrl}/api/inbox-message-state-changes",
            request.url.toString()
        )
        assertEquals(listOf(POST_BODY), capturedBody)
        assertEquals(MEDIA_TYPE, connection.getRequestProperty("Content-Type"))
        assertEquals(MEDIA_TYPE, connection.getRequestProperty("Accept"))
        verify { connection.setRequestProperty("Authorization", "Bearer $TOKEN") }
    }

    @Test
    fun `Test header follows the pre revision rule`() {
        assertTrue(InboxApiRequest.requiresTestHeader("2027-01-15.pre"))
        assertFalse(InboxApiRequest.requiresTestHeader("2027-01-15"))
        assertEquals("true", makeTestRequest().headers["X-Klaviyo-API-Test"])
    }

    @Test
    fun `Makes a single attempt and only accepts 200`() {
        val request = makeTestRequest()
        assertEquals(1, request.maxAttempts)

        listOf(200 to KlaviyoApiRequest.Status.Complete, 202 to KlaviyoApiRequest.Status.Failed)
            .forEach { (code, expected) ->
                withConnectionMock(code = code)
                assertEquals(expected, makeTestRequest().send())
            }
        listOf(429, 503).forEach { code ->
            withConnectionMock(code = code)
            assertEquals(KlaviyoApiRequest.Status.Failed, makeTestRequest().send())
        }
    }

    @Test
    fun `GET URL is exact and keeps the cursor byte for byte`() {
        val cursor = "abc%2Bdef%3D%3D=tail"
        val request = InboxApiRequest.fetchPage(TOKEN, "user+1@example.com/x y", cursor, 100)

        assertEquals(
            "${mockConfig.baseUrl}/api/inbox-messages" +
                "?profile_id=user%2B1%40example.com%2Fx+y&platform=android&page[size]=100" +
                "&page[cursor]=$cursor",
            request.url.toString()
        )
    }

    @Test
    fun `Omits cursor when there is none`() {
        assertFalse(makeTestRequest().url.toString().contains("page[cursor]"))
    }

    @Test
    fun `Response carries code, body and Retry-After seconds`() {
        withConnectionMock(
            code = 429,
            body = "slow down",
            headerFields = mapOf(null to listOf("HTTP/1.1 429"), "Retry-After" to listOf("7"))
        )

        val response = makeTestRequest().execute()

        assertEquals(429, response.code)
        assertEquals("slow down", response.body)
        assertEquals(7_000L, response.retryAfterMs)
        assertTrue(response.sent)
    }

    @Test
    fun `Retry-After header name is case-insensitive`() {
        withConnectionMock(code = 503, headerFields = mapOf("retry-after" to listOf("2")))

        assertEquals(2_000L, makeTestRequest().execute().retryAfterMs)
    }

    @Test
    fun `Retry-After HTTP date or garbage gives null`() {
        listOf("Wed, 21 Oct 2026 07:28:00 GMT", "soon", "-5", "").forEach { value ->
            withConnectionMock(code = 503, headerFields = mapOf("Retry-After" to listOf(value)))
            assertNull(value, makeTestRequest().execute().retryAfterMs)
        }
    }

    @Test
    fun `Offline gives sent false and never touches the network`() {
        every { mockNetworkMonitor.isNetworkConnected() } returns false
        mockkObject(HttpUtil)

        val response = makeTestRequest().execute()

        assertFalse(response.sent)
        assertNull(response.code)
        assertNull(response.body)
        verify(exactly = 0) { HttpUtil.openConnection(any()) }
    }

    @Test
    fun `IOException gives null code and sent true`() {
        val connection = withConnectionMock()
        every { connection.connect() } throws IOException("boom")

        val response = makeTestRequest().execute()

        assertNull(response.code)
        assertTrue(response.sent)
        verify(exactly = 0) { spyLog.warning(any(), any()) }
        verify(exactly = 0) { spyLog.error(any(), any()) }
    }

    @Test
    fun `IOException reading a 200 body gives null code`() {
        val connection = withConnectionMock(code = 200)
        every { connection.inputStream } throws IOException("reset")

        val response = makeTestRequest().execute()

        assertNull(response.code)
        assertTrue(response.sent)
    }

    @Test
    fun `4xx logs no warning or error and does not leak the token`() {
        withConnectionMock(code = 403, body = "forbidden")

        val response = makeTestRequest().execute()

        assertEquals(403, response.code)
        verify(exactly = 0) { spyLog.warning(any(), any()) }
        verify(exactly = 0) { spyLog.error(any(), any()) }
        verify(exactly = 0) { spyLog.wtf(any(), any()) }
        assertFalse(response.toString().contains("forbidden"))
    }
}

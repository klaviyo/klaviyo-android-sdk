package com.klaviyo.mobileInbox

import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxApiServiceImplTest : BaseTest() {

    private val endpoint = "https://inbox.klaviyo.com/messages"
    private lateinit var capturedUrl: URL
    private val mockConnection = mockk<HttpURLConnection>(relaxed = true)

    private val service = InboxApiServiceImpl(endpoint) { url ->
        capturedUrl = url
        mockConnection
    }

    private fun stubResponse(code: Int, body: String) {
        every { mockConnection.responseCode } returns code
        every { mockConnection.inputStream } returns ByteArrayInputStream(body.toByteArray())
    }

    @Test
    fun `builds URL with no params when profile has no identifiers`() = runTest {
        stubResponse(200, "[]")
        service.fetchMessages(InboxProfileParams())
        assertEquals(endpoint, capturedUrl.toString())
    }

    @Test
    fun `builds URL with email query param`() = runTest {
        stubResponse(200, "[]")
        service.fetchMessages(InboxProfileParams(email = "a@b.com"))
        assertTrue(capturedUrl.toString().contains("email=a%40b.com"))
    }

    @Test
    fun `builds URL with all identifiers`() = runTest {
        stubResponse(200, "[]")
        service.fetchMessages(
            InboxProfileParams(anonymousId = "anon", email = "a@b.com", phoneNumber = "+1555")
        )
        val urlString = capturedUrl.toString()
        assertTrue(urlString.contains("anonymous_id=anon"))
        assertTrue(urlString.contains("email=a%40b.com"))
        assertTrue(urlString.contains("phone_number=%2B1555"))
    }

    @Test
    fun `parses valid JSON response`() = runTest {
        val json = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("id", "msg-1")
                    put("timestamp", 1000L)
                    put("title", "Hello")
                    put("body", "World")
                }
            )
        }.toString()
        stubResponse(200, json)

        val result = service.fetchMessages(InboxProfileParams())

        assertEquals(1, result.size)
        assertEquals("msg-1", result[0].id)
        assertEquals(1000L, result[0].timestamp)
        assertEquals("Hello", result[0].title)
        assertEquals("World", result[0].body)
        assertEquals(InboxStatus.UNREAD, result[0].status)
        assertEquals(InboxSource.REMOTE, result[0].source)
    }

    @Test
    fun `returns empty list on non-200 response`() = runTest {
        stubResponse(500, "")
        val result = service.fetchMessages(InboxProfileParams())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns empty list on malformed JSON`() = runTest {
        stubResponse(200, "not json")
        val result = service.fetchMessages(InboxProfileParams())
        assertTrue(result.isEmpty())
    }
}

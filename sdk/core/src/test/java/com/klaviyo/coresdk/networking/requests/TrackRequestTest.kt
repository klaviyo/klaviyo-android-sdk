package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.helpers.StaticClock
import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.KlaviyoEventType
import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.networking.RequestMethod
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest.Companion.BASE_URL
import com.klaviyo.coresdk.networking.requests.TrackRequest.Companion.TRACK_ENDPOINT
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.HttpURLConnection
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TrackRequestTest {

    private val currentTimeMillis = 1234567890000L
    private val isoTime = "2009-02-13T23:31:30+0000"
    private val apiKey = "Fake_Key"
    private val anonId = "a123"
    private val email = "test@test.com"
    private val phone = "+12223334444"
    private val event = "Test Event"

    @Before
    fun setup() {
        KlaviyoConfig.Builder()
            .apiKey(apiKey)
            .applicationContext(mockk())
            .clock(StaticClock(currentTimeMillis))
            .build()
    }

    @Test
    fun `Build Track request with no properties successfully`() {
        val connectionMock = mockk<HttpURLConnection>()
        val keyArgs = mutableListOf<String>()
        val valueArgs = mutableListOf<String>()
        every { connectionMock.setRequestProperty(capture(keyArgs), capture(valueArgs)) } returns Unit

        val event = KlaviyoEventType.CUSTOM(event)
        val profile = Profile()
            .setAnonymousId(anonId)
            .setEmail(email)
            .setPhoneNumber(phone)

        val expectedQueryData = mapOf(
            "company_id" to "Fake_Key"
        )
        val expectedJsonString =
            "{\"data\":{\"type\":\"event\",\"attributes\":{\"metric\":{\"name\":\"$event\"},\"profile\":{\"\$email\":\"$email\",\"\$anonymous\":\"$anonId\",\"\$phone_number\":\"$phone\"},\"time\":\"$isoTime\"}}}"
        val expectedHeaderKeys = listOf("Content-Type", "Accept", "Revision")
        val expectedHeaderValues = listOf("application/json", "application/json", "2022-10-17")

        val request = TrackRequest(apiKey = KlaviyoConfig.apiKey, event, profile)
        request.appendHeaders(connectionMock)

        assertEquals("$BASE_URL/$TRACK_ENDPOINT", request.urlString)
        assertEquals(RequestMethod.POST, request.requestMethod)
        assertEquals(expectedQueryData, request.queryData)
        assertEquals(expectedJsonString, request.payload)
        verify(exactly = 3) {
            connectionMock.setRequestProperty(any(), any())
        }
        assertEquals(keyArgs, expectedHeaderKeys)
        assertEquals(valueArgs, expectedHeaderValues)
    }

    @Test
    fun `Build Track request successfully`() {
        val connectionMock = mockk<HttpURLConnection>()
        val keyArgs = mutableListOf<String>()
        val valueArgs = mutableListOf<String>()
        every { connectionMock.setRequestProperty(capture(keyArgs), capture(valueArgs)) } returns Unit

        val event = KlaviyoEventType.CUSTOM(event)
        val profile = Profile()
            .setAnonymousId(anonId)
            .setEmail(email)
            .setPhoneNumber(phone)
        val properties = Event()
            .setProperty("custom_value", "200")

        val expectedQueryData = mapOf(
            "company_id" to "Fake_Key"
        )
        val expectedJsonString =
            "{\"data\":{\"type\":\"event\",\"attributes\":{\"time\":\"$isoTime\",\"metric\":{\"name\":\"$event\"},\"properties\":{\"custom_value\":\"200\"},\"profile\":{\"\$email\":\"$email\",\"\$anonymous\":\"$anonId\",\"\$phone_number\":\"$phone\"}}}}"
        val expectedHeaderKeys = listOf("Content-Type", "Accept", "Revision")
        val expectedHeaderValues = listOf("application/json", "application/json", "2022-10-17")

        val request = TrackRequest(apiKey = KlaviyoConfig.apiKey, event, profile, properties)
        request.appendHeaders(connectionMock)

        assertEquals("$BASE_URL/$TRACK_ENDPOINT", request.urlString)
        assertEquals(RequestMethod.POST, request.requestMethod)
        assertEquals(expectedQueryData, request.queryData)
        assertEquals(expectedJsonString, request.payload)
        verify(exactly = 3) {
            connectionMock.setRequestProperty(any(), any())
        }
        assertEquals(keyArgs, expectedHeaderKeys)
        assertEquals(valueArgs, expectedHeaderValues)
    }
}

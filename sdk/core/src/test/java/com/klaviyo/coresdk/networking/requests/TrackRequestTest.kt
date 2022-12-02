package com.klaviyo.coresdk.networking.requests

import android.content.Context
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.helpers.StaticClock
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.networking.KlaviyoEvent
import com.klaviyo.coresdk.networking.KlaviyoEventProperties
import com.klaviyo.coresdk.networking.RequestMethod
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest.Companion.BASE_URL
import com.klaviyo.coresdk.networking.requests.TrackRequest.Companion.TRACK_ENDPOINT
import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import java.net.HttpURLConnection
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TrackRequestTest {

    private val currentTimeMillis = 1234567890000L
    private val timestamp = 1234567890L
    private val apiKey = "Fake_Key"
    private val uuid = "a123"

    @Before
    fun setup() {
        val contextMock = mockk<Context>()

        KlaviyoConfig.Builder()
            .apiKey(apiKey)
            .applicationContext(contextMock)
            .networkTimeout(1000)
            .networkFlushInterval(10000)
            .clock(StaticClock(currentTimeMillis))
            .build()

        mockkObject(KlaviyoPreferenceUtils)
        every { KlaviyoPreferenceUtils.readOrGenerateUUID() } returns uuid
    }

    @Test
    fun `Build Track request with no properties successfully`() {
        val connectionMock = mockk<HttpURLConnection>()
        val keyArgs = mutableListOf<String>()
        val valueArgs = mutableListOf<String>()
        every { connectionMock.setRequestProperty(capture(keyArgs), capture(valueArgs)) } returns Unit

        val event = KlaviyoEvent.CUSTOM_EVENT("Test Event")
        val customerProperties = KlaviyoCustomerProperties()
            .setEmail("test@test.com")
            .setPhoneNumber("+12223334444")

        val expectedQueryData = mapOf(
            "company_id" to "Fake_Key"
        )
        val expectedJsonString =
            "{\"data\":{\"type\":\"event\",\"attributes\":{\"metric\":{\"name\":\"Test Event\"},\"profile\":{\"\$email\":\"test@test.com\",\"\$anonymous\":\"a123\",\"\$phone_number\":\"+12223334444\"},\"time\":$timestamp}}}"
        val expectedHeaderKeys = listOf("Content-Type", "Accept", "Revision")
        val expectedHeaderValues = listOf("application/json", "application/json", "2022-10-17")

        val request = TrackRequest(apiKey = KlaviyoConfig.apiKey, event, customerProperties)
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

        val event = KlaviyoEvent.CUSTOM_EVENT("Test Event")
        val customerProperties = KlaviyoCustomerProperties()
            .setEmail("test@test.com")
            .setPhoneNumber("+12223334444")
        val properties = KlaviyoEventProperties()
            .addCustomProperty("custom_value", "200")

        val expectedQueryData = mapOf(
            "company_id" to "Fake_Key"
        )
        val expectedJsonString =
            "{\"data\":{\"type\":\"event\",\"attributes\":{\"time\":$timestamp,\"metric\":{\"name\":\"Test Event\"},\"properties\":{\"custom_value\":\"200\"},\"profile\":{\"\$email\":\"test@test.com\",\"\$anonymous\":\"a123\",\"\$phone_number\":\"+12223334444\"}}}}"
        val expectedHeaderKeys = listOf("Content-Type", "Accept", "Revision")
        val expectedHeaderValues = listOf("application/json", "application/json", "2022-10-17")

        val request = TrackRequest(apiKey = KlaviyoConfig.apiKey, event, customerProperties, properties)
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

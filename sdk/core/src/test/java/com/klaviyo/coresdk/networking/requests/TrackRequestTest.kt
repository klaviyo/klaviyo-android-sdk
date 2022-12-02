package com.klaviyo.coresdk.networking.requests

import android.content.Context
import android.content.SharedPreferences
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.networking.KlaviyoEvent
import com.klaviyo.coresdk.networking.KlaviyoEventProperties
import com.klaviyo.coresdk.networking.RequestMethod
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest.Companion.BASE_URL
import com.klaviyo.coresdk.networking.requests.TrackRequest.Companion.TRACK_ENDPOINT
import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class TrackRequestTest {
    private val contextMock = mock<Context>()

    private val timestamp = 1234567890000L

    @Before
    fun setup() {
        KlaviyoConfig.Builder()
            .apiKey("Fake_Key")
            .applicationContext(contextMock)
            .networkTimeout(1000)
            .networkFlushInterval(10000)
            .clock(StaticClock(timestamp))
            .build()

        val sharedPreferencesMock = Mockito.mock(SharedPreferences::class.java)
        whenever(contextMock.getSharedPreferences(any(), any())).thenReturn(sharedPreferencesMock)
        whenever(
            sharedPreferencesMock.getString(
                KlaviyoPreferenceUtils.KLAVIYO_UUID_KEY,
                ""
            )
        ).thenReturn("a123")
    }

    @Test
    fun `Build Track request with no properties successfully`() {
        val event = KlaviyoEvent.CUSTOM_EVENT("Test Event")

        val customerProperties = KlaviyoCustomerProperties()
        customerProperties.setEmail("test@test.com")
        customerProperties.setPhoneNumber("+12223334444")

        val expectedQueryData = mapOf(
            "company_id" to "Fake_Key"
        )
        val expectedTimestamp = 1234567890L
        val expectedJsonString =
            "{\"data\":{\"type\":\"event\",\"attributes\":{\"metric\":{\"name\":\"Test Event\"},\"profile\":{\"\$email\":\"test@test.com\",\"\$anonymous\":\"a123\",\"\$phone_number\":\"+12223334444\"},\"time\":$expectedTimestamp}}}"

        val request = TrackRequest(apiKey = KlaviyoConfig.apiKey, event, customerProperties)

        assertEquals("$BASE_URL/$TRACK_ENDPOINT", request.urlString)
        assertEquals(RequestMethod.POST, request.requestMethod)
        assertEquals(expectedQueryData, request.queryData)
        assertEquals(expectedJsonString, request.payload)
    }

    @Test
    fun `Build Track request successfully`() {
        val event = KlaviyoEvent.CUSTOM_EVENT("Test Event")

        val customerProperties = KlaviyoCustomerProperties()
        customerProperties.setEmail("test@test.com")
        customerProperties.setPhoneNumber("+12223334444")

        val expectedTimestamp = 1234567890L
        val expectedQueryData = mapOf(
            "company_id" to "Fake_Key"
        )
        val properties = KlaviyoEventProperties()
        properties.addCustomProperty("custom_value", "200")

        val expectedJsonString =
            "{\"data\":{\"type\":\"event\",\"attributes\":{\"time\":$expectedTimestamp,\"metric\":{\"name\":\"Test Event\"},\"properties\":{\"custom_value\":\"200\"},\"profile\":{\"\$email\":\"test@test.com\",\"\$anonymous\":\"a123\",\"\$phone_number\":\"+12223334444\"}}}}"

        val request = TrackRequest(apiKey = KlaviyoConfig.apiKey, event, customerProperties, properties)

        assertEquals("$BASE_URL/$TRACK_ENDPOINT", request.urlString)
        assertEquals(RequestMethod.POST, request.requestMethod)
        assertEquals(expectedQueryData, request.queryData)
        assertEquals(expectedJsonString, request.payload)
    }
}

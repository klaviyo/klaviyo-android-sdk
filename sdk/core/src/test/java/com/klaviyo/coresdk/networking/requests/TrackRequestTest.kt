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
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class TrackRequestTest {
    private val contextMock = mock<Context>()

    @Before
    fun setup() {
        KlaviyoConfig.Builder()
            .apiKey("Fake_Key")
            .applicationContext(contextMock)
            .networkTimeout(1000)
            .networkFlushInterval(10000)
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

        val expectedJsonString =
            "{\"event\":\"Test Event\",\"customer_properties\":{\"\$email\":\"test@test.com\",\"\$anonymous\":\"Android:a123\",\"\$phone_number\":\"+12223334444\"},\"token\":\"Fake_Key\"}"

        val request = TrackRequest(event, customerProperties)
        request.queryData = request.buildKlaviyoJsonQuery()

        Assert.assertEquals("$BASE_URL/$TRACK_ENDPOINT", request.urlString)
        Assert.assertEquals(RequestMethod.GET, request.requestMethod)
        Assert.assertEquals(expectedJsonString, request.queryData)
        Assert.assertEquals(null, request.payload)
    }

    @Test
    fun `Build Track request successfully`() {
        val event = KlaviyoEvent.CUSTOM_EVENT("Test Event")

        val customerProperties = KlaviyoCustomerProperties()
        customerProperties.setEmail("test@test.com")
        customerProperties.setPhoneNumber("+12223334444")

        val properties = KlaviyoEventProperties()
        properties.addCustomProperty("custom_value", "200")

        val expectedJsonString =
            "{\"event\":\"Test Event\",\"customer_properties\":{\"\$email\":\"test@test.com\",\"\$anonymous\":\"Android:a123\",\"\$phone_number\":\"+12223334444\"},\"properties\":{\"custom_value\":\"200\"},\"token\":\"Fake_Key\"}"

        val request = TrackRequest(event, customerProperties, properties)
        request.queryData = request.buildKlaviyoJsonQuery()

        Assert.assertEquals("$BASE_URL/$TRACK_ENDPOINT", request.urlString)
        Assert.assertEquals(RequestMethod.GET, request.requestMethod)
        Assert.assertEquals(expectedJsonString, request.queryData)
        Assert.assertEquals(null, request.payload)
    }
}

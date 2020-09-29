package com.klaviyo.coresdk.networking.requests

import android.content.Context
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.networking.KlaviyoEventProperties
import com.klaviyo.coresdk.networking.RequestMethod
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest.Companion.ANON_KEY
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest.Companion.BASE_URL
import com.klaviyo.coresdk.networking.requests.TrackRequest.Companion.TRACK_ENDPOINT
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert
import org.junit.Before
import org.junit.Test

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
    }

    @Test
    fun `Build Track request with no properties successfully`() {
        val event = "Test Event"

        val customerPropertiesSpy = spy(KlaviyoCustomerProperties())
        customerPropertiesSpy.addEmail("test@test.com")
        customerPropertiesSpy.addPhoneNumber("+12223334444")


        val expectedJsonString = "{\"event\":\"Test Event\",\"customer_properties\":{\"\$email\":\"test@test.com\",\"\$anonymous\":\"Android:a123\",\"\$phone_number\":\"+12223334444\"},\"token\":\"Fake_Key\"}"

        val request = TrackRequest(event, customerPropertiesSpy)

        doAnswer { customerPropertiesSpy.propertyMap[ANON_KEY] = "Android:a123" }.whenever(customerPropertiesSpy).addAnonymousId()

        request.queryData = request.buildKlaviyoJsonQuery()

        Assert.assertEquals("$BASE_URL/$TRACK_ENDPOINT", request.urlString)
        Assert.assertEquals(RequestMethod.GET, request.requestMethod)
        Assert.assertEquals(expectedJsonString, request.queryData)
        Assert.assertEquals(null, request.payload)
    }

    @Test
    fun `Build Track request successfully`() {
        val event = "Test Event"

        val customerPropertiesSpy = spy(KlaviyoCustomerProperties())
        customerPropertiesSpy.addEmail("test@test.com")
        customerPropertiesSpy.addPhoneNumber("+12223334444")

        val propertiesSpy = spy(KlaviyoEventProperties())
        propertiesSpy.addCustomProp("custom_value", "200")

        val expectedJsonString = "{\"event\":\"Test Event\",\"customer_properties\":{\"\$email\":\"test@test.com\",\"\$anonymous\":\"Android:a123\",\"\$phone_number\":\"+12223334444\"},\"properties\":{\"custom_value\":\"200\"},\"token\":\"Fake_Key\"}"

        val request = TrackRequest(event, customerPropertiesSpy, propertiesSpy)

        doAnswer { customerPropertiesSpy.propertyMap[ANON_KEY] = "Android:a123" }.whenever(customerPropertiesSpy).addAnonymousId()

        request.queryData = request.buildKlaviyoJsonQuery()

        Assert.assertEquals("$BASE_URL/$TRACK_ENDPOINT", request.urlString)
        Assert.assertEquals(RequestMethod.GET, request.requestMethod)
        Assert.assertEquals(expectedJsonString, request.queryData)
        Assert.assertEquals(null, request.payload)
    }
}
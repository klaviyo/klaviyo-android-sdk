package com.klaviyo.coresdk.networking.requests

import android.content.Context
import com.klaviyo.coresdk.KlaviyoConfig
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
        val customerProperties = hashMapOf<String, Any>("\$email" to "test@test.com", "\$phone_number" to "+12223334444")

        val expectedJsonString = "{\"event\":\"Test Event\",\"customer_properties\":{\"\$email\":\"test@test.com\",\"\$anonymous\":\"Android:a123\",\"\$phone_number\":\"+12223334444\"},\"token\":\"Fake_Key\"}"

        val requestSpy = spy(TrackRequest(event, customerProperties))

        doAnswer { customerProperties[ANON_KEY] = "Android:a123" }.whenever(requestSpy).addAnonymousIdToProps(any())

        requestSpy.queryData = requestSpy.buildKlaviyoJsonQuery()

        Assert.assertEquals("$BASE_URL/$TRACK_ENDPOINT", requestSpy.urlString)
        Assert.assertEquals(RequestMethod.GET, requestSpy.requestMethod)
        Assert.assertEquals(expectedJsonString, requestSpy.queryData)
        Assert.assertEquals(null, requestSpy.payload)
    }

    @Test
    fun `Build Track request successfully`() {
        val event = "Test Event"
        val customerProperties = hashMapOf<String, Any>("\$email" to "test@test.com", "\$phone_number" to "+12223334444")
        val properties = hashMapOf<String, Any>("custom_value" to "200")

        val expectedJsonString = "{\"event\":\"Test Event\",\"customer_properties\":{\"\$email\":\"test@test.com\",\"\$anonymous\":\"Android:a123\",\"\$phone_number\":\"+12223334444\"},\"properties\":{\"custom_value\":\"200\"},\"token\":\"Fake_Key\"}"

        val requestSpy = spy(TrackRequest(event, customerProperties, properties))

        doAnswer { customerProperties[ANON_KEY] = "Android:a123" }.whenever(requestSpy).addAnonymousIdToProps(any())

        requestSpy.queryData = requestSpy.buildKlaviyoJsonQuery()

        Assert.assertEquals("$BASE_URL/$TRACK_ENDPOINT", requestSpy.urlString)
        Assert.assertEquals(RequestMethod.GET, requestSpy.requestMethod)
        Assert.assertEquals(expectedJsonString, requestSpy.queryData)
        Assert.assertEquals(null, requestSpy.payload)
    }
}
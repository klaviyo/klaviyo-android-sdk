package com.klaviyo.coresdk.networking

import android.content.Context
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.IdentifyRequest.Companion.IDENTIFY_ENDPOINT
import com.klaviyo.coresdk.networking.KlaviyoRequest.Companion.BASE_URL
import com.klaviyo.coresdk.networking.TrackRequest.Companion.TRACK_ENDPOINT
import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.net.URL

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
    fun `Build Track request successfully`() {
        val event = "Test Event"
        val customerProperties = hashMapOf("\$email" to "test@test.com", "\$phone_number" to "+12223334444")

        val expectedJsonString = "{\"customer_properties\":{\"\$phone_number\":\"+12223334444\",\"\$email\":\"test@test.com\"},\"event\":\"Test Event\",\"token\":\"Fake_Key\"}"

        val request = TrackRequest(event, customerProperties)
        request.headerData = request.buildKlaviyoJsonHeader()

        Assert.assertEquals(URL("$BASE_URL/$TRACK_ENDPOINT"), request.url)
        Assert.assertEquals(RequestMethod.GET, request.requestMethod)
        Assert.assertEquals(expectedJsonString, request.headerData)
        Assert.assertEquals(null, request.payload)
    }

    @Test
    fun `Build Track request with no properties`() {
        val event = "Test Event"
        val customerProperties = hashMapOf("\$email" to "test@test.com", "\$phone_number" to "+12223334444")
        val properties = hashMapOf("custom_value" to "200")

        val expectedJsonString = "{\"customer_properties\":{\"\$phone_number\":\"+12223334444\",\"\$email\":\"test@test.com\"},\"event\":\"Test Event\",\"properties\":{\"custom_value\":\"200\"},\"token\":\"Fake_Key\"}"

        val request = TrackRequest(event, customerProperties, properties)
        request.headerData = request.buildKlaviyoJsonHeader()

        Assert.assertEquals(URL("$BASE_URL/$TRACK_ENDPOINT"), request.url)
        Assert.assertEquals(RequestMethod.GET, request.requestMethod)
        Assert.assertEquals(expectedJsonString, request.headerData)
        Assert.assertEquals(null, request.payload)
    }
}
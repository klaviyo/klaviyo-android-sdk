package com.klaviyo.coresdk.networking

import android.content.Context
import com.klaviyo.coresdk.KlaviyoConfig
import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.net.URL

class IdentifyRequestTest {
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
    fun `Build Identify request successfully`() {
        val event = "Test Event"
        val properties = hashMapOf("custom_value" to "200")

        val expectedJsonString = "{\"event\":\"Test Event\",\"properties\":{\"custom_value\":\"200\"},\"token\":\"Fake_Key\"}"

        val request = IdentifyRequest(event, properties = properties)
        request.headerData = request.buildKlaviyoJsonHeader()

        Assert.assertEquals(URL("${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}"), request.url)
        Assert.assertEquals(RequestMethod.GET, request.requestMethod)
        Assert.assertEquals(expectedJsonString, request.headerData)
        Assert.assertEquals(null, request.payload)
    }
}
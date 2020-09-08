package com.klaviyo.coresdk.networking.requests

import android.content.Context
import com.klaviyo.coresdk.ConfigFileUtils
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.RequestMethod
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest.Companion.ANON_KEY
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.net.URL
import javax.net.ssl.HttpsURLConnection

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
        val properties = hashMapOf("custom_value" to "200")

        val expectedJsonString = "{\"properties\":{\"custom_value\":\"200\"},\"token\":\"Fake_Key\"}"

        val request = IdentifyRequest(properties = properties)
        request.queryData = request.buildKlaviyoJsonQuery()

        Assert.assertEquals("${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}", request.urlString)
        Assert.assertEquals(RequestMethod.GET, request.requestMethod)
        Assert.assertEquals(expectedJsonString, request.queryData)
        Assert.assertEquals(null, request.payload)
    }

    @Test
    fun `Add anonymous ID to property map successfully`() {
        val properties = hashMapOf("custom_value" to "200")
        val request = IdentifyRequest(properties = properties)

        val requestSpy = spy(request)

        doAnswer { properties[ANON_KEY] = "Android:a123" }.whenever(requestSpy).addAnonymousIdToProps()

        requestSpy.addAnonymousIdToProps()

        Assert.assertEquals("{custom_value=200, \$anonymous=Android:a123}", properties.toString())
    }
}
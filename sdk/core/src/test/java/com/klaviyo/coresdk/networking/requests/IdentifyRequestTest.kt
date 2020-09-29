package com.klaviyo.coresdk.networking.requests

import android.content.Context
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.RequestMethod
import com.klaviyo.coresdk.networking.UserInfo
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest.Companion.ANON_KEY
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert
import org.junit.Before
import org.junit.Test

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
        val properties = hashMapOf<String, Any>("custom_value" to "200", "\$email" to "test@test.com")

        val expectedJsonString = "{\"properties\":{\"custom_value\":\"200\",\"\$email\":\"test@test.com\",\"\$anonymous\":\"Android:a123\"},\"token\":\"Fake_Key\"}"

        val requestSpy = spy(IdentifyRequest(properties = properties))

        doAnswer { properties[ANON_KEY] = "Android:a123" }.whenever(requestSpy).addAnonymousIdToProps(any())

        requestSpy.queryData = requestSpy.buildKlaviyoJsonQuery()

        Assert.assertEquals("${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}", requestSpy.urlString)
        Assert.assertEquals(RequestMethod.GET, requestSpy.requestMethod)
        Assert.assertEquals(expectedJsonString, requestSpy.queryData)
        Assert.assertEquals(null, requestSpy.payload)
    }

    @Test
    fun `Build Identify request successfully appends stored email address`() {
        val properties = hashMapOf<String, Any>("custom_value" to "200")

        val expectedJsonString = "{\"properties\":{\"custom_value\":\"200\",\"\$email\":\"test@test.com\",\"\$anonymous\":\"Android:a123\"},\"token\":\"Fake_Key\"}"

        val requestSpy = spy(IdentifyRequest(properties = properties))

        doAnswer { properties[ANON_KEY] = "Android:a123" }.whenever(requestSpy).addAnonymousIdToProps(any())

        UserInfo.email = "test@test.com"
        requestSpy.queryData = requestSpy.buildKlaviyoJsonQuery()

        Assert.assertEquals("${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}", requestSpy.urlString)
        Assert.assertEquals(RequestMethod.GET, requestSpy.requestMethod)
        Assert.assertEquals(expectedJsonString, requestSpy.queryData)
        Assert.assertEquals(null, requestSpy.payload)
    }
}
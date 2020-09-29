package com.klaviyo.coresdk.networking.requests

import android.content.Context
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.networking.RequestMethod
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
        val propertiesSpy = spy(KlaviyoCustomerProperties())
        propertiesSpy.addCustomProp("custom_value", "200")

        val expectedJsonString = "{\"properties\":{\"custom_value\":\"200\",\"\$anonymous\":\"Android:a123\"},\"token\":\"Fake_Key\"}"

        val request = IdentifyRequest(properties = propertiesSpy)

        doAnswer { propertiesSpy.propertyMap[ANON_KEY] = "Android:a123" }.whenever(propertiesSpy).addAnonymousId()

        request.queryData = request.buildKlaviyoJsonQuery()

        Assert.assertEquals("${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}", request.urlString)
        Assert.assertEquals(RequestMethod.GET, request.requestMethod)
        Assert.assertEquals(expectedJsonString, request.queryData)
        Assert.assertEquals(null, request.payload)
    }

    @Test
    fun `Build Identify request with nested map of properties successfully`() {
        val propertiesSpy = spy(KlaviyoCustomerProperties())
        val innerMap = mapOf("amount" to "2", "name" to "item", "props" to mapOf("weight" to "0.1", "diameter" to "50"))
        propertiesSpy.addCustomProp("custom_value", innerMap)

        val expectedJsonString = "{\"properties\":{\"custom_value\":{\"name\":\"item\",\"amount\":\"2\",\"props\":{\"diameter\":\"50\",\"weight\":\"0.1\"}},\"\$anonymous\":\"Android:a123\"},\"token\":\"Fake_Key\"}"

        val request = IdentifyRequest(properties = propertiesSpy)

        doAnswer { propertiesSpy.propertyMap[ANON_KEY] = "Android:a123" }.whenever(propertiesSpy).addAnonymousId()

        request.queryData = request.buildKlaviyoJsonQuery()

        Assert.assertEquals("${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}", request.urlString)
        Assert.assertEquals(RequestMethod.GET, request.requestMethod)
        Assert.assertEquals(expectedJsonString, request.queryData)
        Assert.assertEquals(null, request.payload)
    }
}
package com.klaviyo.coresdk.networking.requests

import android.content.Context
import android.content.SharedPreferences
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.networking.RequestMethod
import com.klaviyo.coresdk.networking.UserInfo
import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

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

        val sharedPreferencesMock = Mockito.mock(SharedPreferences::class.java)
        whenever(contextMock.getSharedPreferences(any(), any())).thenReturn(sharedPreferencesMock)
        whenever(sharedPreferencesMock.getString(KlaviyoPreferenceUtils.KLAVIYO_UUID_KEY, "")).thenReturn("a123")
    }

    @Test
    fun `Build Identify request successfully`() {
        val properties = KlaviyoCustomerProperties()
        properties.setEmail("test@test.com")
        properties.addCustomProperty("custom_value", "200")

        val expectedJsonString = "{\"properties\":{\"\$email\":\"test@test.com\",\"custom_value\":\"200\",\"\$anonymous\":\"Android:a123\"},\"token\":\"Fake_Key\"}"

        val request = IdentifyRequest(properties = properties)
        request.queryData = request.buildKlaviyoJsonQuery()

        Assert.assertEquals("${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}", request.urlString)
        Assert.assertEquals(RequestMethod.GET, request.requestMethod)
        Assert.assertEquals(expectedJsonString, request.queryData)
        Assert.assertEquals(null, request.payload)
    }

    @Test
    fun `Build Identify request with nested map of properties successfully`() {
        val properties = KlaviyoCustomerProperties()
        val innerMap = hashMapOf("amount" to "2", "name" to "item", "props" to mapOf("weight" to "0.1", "diameter" to "50"))
        properties.addCustomProperty("custom_value", innerMap)

        val expectedJsonString = "{\"properties\":{\"custom_value\":{\"name\":\"item\",\"amount\":\"2\",\"props\":{\"diameter\":\"50\",\"weight\":\"0.1\"}},\"\$anonymous\":\"Android:a123\"},\"token\":\"Fake_Key\"}"

        val request = IdentifyRequest(properties = properties)
        request.queryData = request.buildKlaviyoJsonQuery()

        Assert.assertEquals("${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}", request.urlString)
        Assert.assertEquals(RequestMethod.GET, request.requestMethod)
        Assert.assertEquals(expectedJsonString, request.queryData)
        Assert.assertEquals(null, request.payload)
    }

    @Test
    fun `Build Identify request with append properties successfully`() {
        val properties = KlaviyoCustomerProperties()
        properties.addAppendProperty("append_key", "value")
        properties.addAppendProperty("append_key2", "value2")

        val expectedJsonString = "{\"properties\":{\"\$anonymous\":\"Android:a123\",\"\$append\":{\"append_key\":\"value\",\"append_key2\":\"value2\"}},\"token\":\"Fake_Key\"}"

        val request = IdentifyRequest(properties = properties)
        request.queryData = request.buildKlaviyoJsonQuery()

        Assert.assertEquals("${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}", request.urlString)
        Assert.assertEquals(RequestMethod.GET, request.requestMethod)
        Assert.assertEquals(expectedJsonString, request.queryData)
        Assert.assertEquals(null, request.payload)
    }

    @Test
    fun `Append property keys overwrite previous values if redeclared`() {
        val properties = KlaviyoCustomerProperties()
        properties.addAppendProperty("append_key", "value")
        properties.addAppendProperty("append_key", "valueAgain")

        val expectedJsonString = "{\"properties\":{\"\$anonymous\":\"Android:a123\",\"\$append\":{\"append_key\":\"valueAgain\"}},\"token\":\"Fake_Key\"}"

        val request = IdentifyRequest(properties = properties)
        request.queryData = request.buildKlaviyoJsonQuery()

        Assert.assertEquals(expectedJsonString, request.queryData)
    }

    @Test
    fun `Build Identify request successfully appends stored email address`() {
        UserInfo.email = "test@test.com"

        val properties = KlaviyoCustomerProperties()
        properties.addCustomProperty("custom_value", "200")

        val expectedJsonString = "{\"properties\":{\"\$email\":\"test@test.com\",\"custom_value\":\"200\",\"\$anonymous\":\"Android:a123\"},\"token\":\"Fake_Key\"}"

        val request = IdentifyRequest(properties = properties)
        request.queryData = request.buildKlaviyoJsonQuery()

        Assert.assertEquals("${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}", request.urlString)
        Assert.assertEquals(RequestMethod.GET, request.requestMethod)
        Assert.assertEquals(expectedJsonString, request.queryData)
        Assert.assertEquals(null, request.payload)
    }
}
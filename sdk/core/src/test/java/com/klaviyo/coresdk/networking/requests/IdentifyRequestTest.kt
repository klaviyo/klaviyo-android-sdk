package com.klaviyo.coresdk.networking.requests

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.networking.RequestMethod
import com.klaviyo.coresdk.networking.UserInfo
import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mockStatic

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

        val sharedPreferencesMock = mock<SharedPreferences>()
        whenever(contextMock.getSharedPreferences(any(), any())).thenReturn(sharedPreferencesMock)
        whenever(
            sharedPreferencesMock.getString(
                KlaviyoPreferenceUtils.KLAVIYO_UUID_KEY,
                ""
            )
        ).thenReturn("a123")
    }

    @Test
    fun `Build Identify request successfully`() {
        val properties = KlaviyoCustomerProperties()
            .setEmail("test@test.com")
            .addCustomProperty("custom_value", "200")
        val encodedString = "some base64 encoded string"
        val expectedQueryData = mapOf(
            "data" to encodedString
        )

        val expectedJsonString = "{\"properties\":{\"\$email\":\"test@test.com\",\"custom_value\":\"200\",\"\$anonymous\":\"a123\"},\"token\":\"Fake_Key\"}"

        mockStatic(Base64::class.java).use { mockedStatic ->
            mockedStatic.`when`<String> { Base64.encodeToString(eq(expectedJsonString.toByteArray()), eq(Base64.NO_WRAP)) }
                .thenReturn(encodedString)
            val request = IdentifyRequest(apiKey = KlaviyoConfig.apiKey, properties = properties)
            assertEquals(
                "${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}",
                request.urlString
            )
            assertEquals(RequestMethod.GET, request.requestMethod)
            assertEquals(expectedQueryData, request.queryData)
            assertEquals(null, request.payload)
        }
    }

    @Test
    fun `Build Identify request with nested map of properties successfully`() {
        val properties = KlaviyoCustomerProperties()
        val innerMap = hashMapOf(
            "amount" to "2",
            "name" to "item",
            "props" to mapOf("weight" to "0.1", "diameter" to "50")
        )
        val encodedString = "some base64 encoded string"
        val expectedQueryData = mapOf(
            "data" to encodedString
        )
        properties.addCustomProperty("custom_value", innerMap)

        val expectedJsonString =
            "{\"properties\":{\"custom_value\":{\"name\":\"item\",\"amount\":\"2\",\"props\":{\"diameter\":\"50\",\"weight\":\"0.1\"}},\"\$anonymous\":\"a123\"},\"token\":\"Fake_Key\"}"

        mockStatic(Base64::class.java).use { mockedStatic ->
            mockedStatic.`when`<String> { Base64.encodeToString(eq(expectedJsonString.toByteArray()), eq(Base64.NO_WRAP)) }
                .thenReturn(encodedString)
            val request = IdentifyRequest(apiKey = KlaviyoConfig.apiKey, properties = properties)

            assertEquals(
                "${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}",
                request.urlString
            )
            assertEquals(RequestMethod.GET, request.requestMethod)
            assertEquals(expectedQueryData, request.queryData)
            assertEquals(null, request.payload)
        }
    }

    @Test
    fun `Build Identify request with append properties successfully`() {
        val properties = KlaviyoCustomerProperties()
            .addAppendProperty("append_key", "value")
            .addAppendProperty("append_key2", "value2")

        val encodedString = "some base64 encoded string"
        val expectedQueryData = mapOf(
            "data" to encodedString
        )
        val expectedJsonString =
            "{\"properties\":{\"\$anonymous\":\"a123\",\"\$append\":{\"append_key\":\"value\",\"append_key2\":\"value2\"}},\"token\":\"Fake_Key\"}"

        mockStatic(Base64::class.java).use { mockedStatic ->
            mockedStatic.`when`<String> { Base64.encodeToString(eq(expectedJsonString.toByteArray()), eq(Base64.NO_WRAP)) }
                .thenReturn(encodedString)
            val request = IdentifyRequest(apiKey = KlaviyoConfig.apiKey, properties = properties)

            assertEquals(
                "${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}",
                request.urlString
            )
            assertEquals(RequestMethod.GET, request.requestMethod)
            assertEquals(expectedQueryData, request.queryData)
            assertEquals(null, request.payload)
        }
    }

    @Test
    fun `Append property keys overwrite previous values if redeclared`() {
        val properties = KlaviyoCustomerProperties()
        properties.addAppendProperty("append_key", "value")
        properties.addAppendProperty("append_key", "valueAgain")

        val encodedString = "some base64 encoded string"
        val expectedQueryData = mapOf(
            "data" to encodedString
        )
        val expectedJsonString =
            "{\"properties\":{\"\$anonymous\":\"a123\",\"\$append\":{\"append_key\":\"valueAgain\"}},\"token\":\"Fake_Key\"}"

        mockStatic(Base64::class.java).use { mockedStatic ->
            mockedStatic.`when`<String> { Base64.encodeToString(eq(expectedJsonString.toByteArray()), eq(Base64.NO_WRAP)) }
                .thenReturn(encodedString)

            val request = IdentifyRequest(apiKey = KlaviyoConfig.apiKey, properties = properties)

            assertEquals(expectedQueryData, request.queryData)
        }
    }

    @Test
    fun `Build Identify request successfully appends stored email address`() {
        UserInfo.email = "test@test.com"

        val properties = KlaviyoCustomerProperties()
        properties.addCustomProperty("custom_value", "200")

        val encodedString = "some base64 encoded string"
        val expectedQueryData = mapOf(
            "data" to encodedString
        )
        val expectedJsonString =
            "{\"properties\":{\"\$email\":\"test@test.com\",\"custom_value\":\"200\",\"\$anonymous\":\"a123\"},\"token\":\"Fake_Key\"}"

        mockStatic(Base64::class.java).use { mockedStatic ->
            mockedStatic.`when`<String> { Base64.encodeToString(eq(expectedJsonString.toByteArray()), eq(Base64.NO_WRAP)) }
                .thenReturn(encodedString)

            val request = IdentifyRequest(apiKey = KlaviyoConfig.apiKey, properties = properties)

            assertEquals(
                "${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}",
                request.urlString
            )
            assertEquals(RequestMethod.GET, request.requestMethod)
            assertEquals(expectedQueryData, request.queryData)
            assertEquals(null, request.payload)
        }
    }

    @Test
    fun `Append property after request does not change existing request`() {
        val properties = KlaviyoCustomerProperties()
        properties.addAppendProperty("append_key", "value")

        val encodedString = "some base64 encoded string"
        val expectedQueryData = mapOf(
            "data" to encodedString
        )
        val expectedJsonString =
            "{\"properties\":{\"\$anonymous\":\"a123\",\"\$append\":{\"append_key\":\"value\"}},\"token\":\"Fake_Key\"}"

        mockStatic(Base64::class.java).use { mockedStatic ->
            mockedStatic.`when`<String> { Base64.encodeToString(eq(expectedJsonString.toByteArray()), eq(Base64.NO_WRAP)) }
                .thenReturn(encodedString)

            val request = IdentifyRequest(apiKey = KlaviyoConfig.apiKey, properties = properties)
            properties.addAppendProperty("append_key_again", "value_again")

            assertEquals(expectedQueryData, request.queryData)
        }
    }
}

package com.klaviyo.coresdk.networking.requests

import android.util.Base64
import com.klaviyo.coresdk.BuildConfig
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.networking.RequestMethod
import com.klaviyo.coresdk.networking.UserInfo
import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class IdentifyRequestTest {
    private val apiKey = "Fake_Key"
    private val uuid = "a123"
    private val encodedString = "EncodedString64"

    @Before
    fun setup() {
        clearAllMocks()
        mockkObject(KlaviyoPreferenceUtils)
        every { KlaviyoPreferenceUtils.readOrGenerateUUID() } returns uuid
    }

    private fun withMockBase64(expectedString: String) {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(eq(expectedString.toByteArray()), eq(Base64.NO_WRAP)) } returns encodedString
    }

    @Test
    fun `uses the correct endpoint`() {
        val customerProperties = KlaviyoCustomerProperties()
        customerProperties.setEmail("test@test.com")

        val expectedUrlString = "${BuildConfig.KLAVIYO_SERVER_URL}/api/identify"
        val expectedString = "{\"properties\":{\"\$email\":\"test@test.com\",\"\$anonymous\":\"a123\"},\"token\":\"Fake_Key\"}"

        withMockBase64(expectedString)

        val actualUrlString = IdentifyRequest(
            apiKey = apiKey,
            properties = customerProperties,
        ).urlString

        assertEquals(expectedUrlString, actualUrlString)
    }

    @Test
    fun `uses the correct method`() {
        val customerProperties = KlaviyoCustomerProperties()
        customerProperties.setEmail("test@test.com")

        val expectedMethod = RequestMethod.GET
        val expectedString = "{\"properties\":{\"\$email\":\"test@test.com\",\"\$anonymous\":\"a123\"},\"token\":\"Fake_Key\"}"

        withMockBase64(expectedString)

        val actualMethod = IdentifyRequest(
            apiKey = apiKey,
            properties = customerProperties,
        ).requestMethod

        assertEquals(expectedMethod, actualMethod)
    }

    @Test
    fun `does not set a payload`() {
        UserInfo.email = ""

        val customerProperties = KlaviyoCustomerProperties()

        val expectedPayload = null
        val expectedString = "{\"properties\":{\"\$anonymous\":\"a123\"},\"token\":\"Fake_Key\"}"

        withMockBase64(expectedString)

        val actualPayload = IdentifyRequest(
            apiKey = apiKey,
            properties = customerProperties,
        ).payload

        assertEquals(expectedPayload, actualPayload)
    }

    @Test
    fun `queryData includes api key and anonymous`() {
        UserInfo.email = ""

        val customerProperties = KlaviyoCustomerProperties()

        val expectedString = "{\"properties\":{\"\$anonymous\":\"a123\"},\"token\":\"Fake_Key\"}"
        val expectedQueryData = mapOf("data" to encodedString)

        withMockBase64(expectedString = expectedString)
        val actualQueryData = IdentifyRequest(
            apiKey = apiKey,
            properties = customerProperties,
        ).queryData
        assertEquals(expectedQueryData, actualQueryData)
    }

    @Test
    fun `Build Identify request successfully`() {
        val properties = KlaviyoCustomerProperties()
            .setEmail("test@test.com")
            .addCustomProperty("custom_value", "200")
        val expectedQueryData = mapOf(
            "data" to encodedString
        )
        val expectedJsonString = "{\"properties\":{\"\$email\":\"test@test.com\",\"custom_value\":\"200\",\"\$anonymous\":\"a123\"},\"token\":\"Fake_Key\"}"

        withMockBase64(expectedString = expectedJsonString)

        val request = IdentifyRequest(apiKey = apiKey, properties = properties)
        assertEquals(
            "${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}",
            request.urlString
        )
        assertEquals(RequestMethod.GET, request.requestMethod)
        assertEquals(expectedQueryData, request.queryData)
        assertEquals(null, request.payload)
    }

    @Test
    fun `Build Identify request with nested map of properties successfully`() {
        val properties = KlaviyoCustomerProperties()
        val innerMap = hashMapOf(
            "amount" to "2",
            "name" to "item",
            "props" to mapOf("weight" to "0.1", "diameter" to "50")
        )
        val expectedQueryData = mapOf(
            "data" to encodedString
        )
        properties.addCustomProperty("custom_value", innerMap)
        properties.setEmail("test@test.com")

        val expectedJsonString =
            "{\"properties\":{\"custom_value\":{\"name\":\"item\",\"amount\":\"2\",\"props\":{\"diameter\":\"50\",\"weight\":\"0.1\"}},\"\$email\":\"test@test.com\",\"\$anonymous\":\"a123\"},\"token\":\"Fake_Key\"}"
        withMockBase64(expectedString = expectedJsonString)
        val request = IdentifyRequest(apiKey = apiKey, properties = properties)

        assertEquals(
            "${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}",
            request.urlString
        )
        assertEquals(RequestMethod.GET, request.requestMethod)
        assertEquals(expectedQueryData, request.queryData)
        assertEquals(null, request.payload)
    }

    @Test
    fun `Build Identify request with append properties successfully`() {
        val properties = KlaviyoCustomerProperties()
            .addAppendProperty("append_key", "value")
            .addAppendProperty("append_key2", "value2")
        properties.setEmail("test@test.com")

        val expectedQueryData = mapOf(
            "data" to encodedString
        )
        val expectedJsonString =
            "{\"properties\":{\"\$email\":\"test@test.com\",\"\$anonymous\":\"a123\",\"\$append\":{\"append_key\":\"value\",\"append_key2\":\"value2\"}},\"token\":\"Fake_Key\"}"
        withMockBase64(expectedString = expectedJsonString)

        val request = IdentifyRequest(apiKey = apiKey, properties = properties)

        assertEquals(
            "${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}",
            request.urlString
        )
        assertEquals(RequestMethod.GET, request.requestMethod)
        assertEquals(expectedQueryData, request.queryData)
        assertEquals(null, request.payload)
    }

    @Test
    fun `Append property keys overwrite previous values if redeclared`() {
        val properties = KlaviyoCustomerProperties()
        properties.addAppendProperty("append_key", "value")
        properties.addAppendProperty("append_key", "valueAgain")
        properties.setEmail("test@test.com")

        val expectedQueryData = mapOf(
            "data" to encodedString
        )
        val expectedJsonString =
            "{\"properties\":{\"\$email\":\"test@test.com\",\"\$anonymous\":\"a123\",\"\$append\":{\"append_key\":\"valueAgain\"}},\"token\":\"Fake_Key\"}"
        withMockBase64(expectedString = expectedJsonString)

        val request = IdentifyRequest(apiKey = apiKey, properties = properties)

        assertEquals(expectedQueryData, request.queryData)
    }

    @Test
    fun `Build Identify request successfully appends stored email address`() {
        UserInfo.email = "test@test.com"

        val properties = KlaviyoCustomerProperties()
        properties.addCustomProperty("custom_value", "200")

        val expectedQueryData = mapOf(
            "data" to encodedString
        )
        val expectedJsonString =
            "{\"properties\":{\"\$email\":\"test@test.com\",\"custom_value\":\"200\",\"\$anonymous\":\"a123\"},\"token\":\"Fake_Key\"}"
        withMockBase64(expectedString = expectedJsonString)

        val request = IdentifyRequest(apiKey = apiKey, properties = properties)

        assertEquals(
            "${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}",
            request.urlString
        )
        assertEquals(RequestMethod.GET, request.requestMethod)
        assertEquals(expectedQueryData, request.queryData)
        assertEquals(null, request.payload)
    }

    @Test
    fun `Append property after request does not change existing request`() {
        val properties = KlaviyoCustomerProperties()
        properties.addAppendProperty("append_key", "value")
        properties.setEmail("test@test.com")

        val expectedQueryData = mapOf(
            "data" to encodedString
        )
        val expectedJsonString =
            "{\"properties\":{\"\$email\":\"test@test.com\",\"\$anonymous\":\"a123\",\"\$append\":{\"append_key\":\"value\"}},\"token\":\"Fake_Key\"}"
        withMockBase64(expectedString = expectedJsonString)

        val request = IdentifyRequest(apiKey = apiKey, properties = properties)
        properties.addAppendProperty("append_key_again", "value_again")

        assertEquals(expectedQueryData, request.queryData)
    }
}

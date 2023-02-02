package com.klaviyo.coresdk.networking.requests

import android.util.Base64
import com.klaviyo.coresdk.BuildConfig
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.helpers.BaseTest
import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.networking.RequestMethod
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class IdentifyRequestTest : BaseTest() {
    private val encodedString = "Expected json string, base64 encoded"
    private val expectedQueryData = mapOf("data" to encodedString)
    private val expectedUrl = "${KlaviyoRequest.BASE_URL}/${IdentifyRequest.IDENTIFY_ENDPOINT}"
    private var profile = Profile()

    override fun setup() {
        super.setup()

        Klaviyo.initialize(API_KEY, contextMock)
        profile.setAnonymousId(ANON_ID) // start all tests with an empty profile and base64 mock for it
        withMockBase64("{\"properties\":{\"\$anonymous\":\"$ANON_ID\"},\"token\":\"$API_KEY\"}")
    }

    private fun withMockBase64(expectedString: String) {
        // We use the android base64 dependency, which means we have to mock it in our unit tests
        // This mocks the encodeToString to only returns a stub value if the json string matched
        // and fail with a useful error message if it does not
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            val arg = it.invocation.args[0] as? ByteArray
            val str = arg?.let { it1 -> String(it1) }
            if (str == expectedString) {
                encodedString
            } else {
                fail("Unexpected json string: \n  Expected: $expectedString \n    Actual: $str")
                ""
            }
        }
    }

    @Test
    fun `uses the correct endpoint`() {
        val expectedUrlString = "${BuildConfig.KLAVIYO_SERVER_URL}/api/identify"
        val actualUrlString = IdentifyRequest(
            apiKey = API_KEY,
            profile = profile,
        ).urlString

        assertEquals(expectedUrlString, actualUrlString)
    }

    @Test
    fun `uses the correct method`() {
        val expectedMethod = RequestMethod.GET
        val actualMethod = IdentifyRequest(
            apiKey = API_KEY,
            profile = profile,
        ).requestMethod

        assertEquals(expectedMethod, actualMethod)
    }

    @Test
    fun `queryData includes api key and anonymous`() {
        val actualQueryData = IdentifyRequest(
            apiKey = API_KEY,
            profile = profile,
        ).queryData

        assertEquals(expectedQueryData, actualQueryData)
    }

    @Test
    fun `does not set a payload`() {
        val actualPayload = IdentifyRequest(
            apiKey = API_KEY,
            profile = profile,
        ).payload

        assertNull(actualPayload)
    }

    @Test
    fun `Build Identify request successfully`() {
        profile.setEmail(EMAIL)
        profile.setProperty("custom_value", "200")

        withMockBase64("{\"properties\":{\"\$email\":\"$EMAIL\",\"custom_value\":\"200\",\"\$anonymous\":\"$ANON_ID\"},\"token\":\"$API_KEY\"}")

        val request = IdentifyRequest(apiKey = API_KEY, profile = profile)

        assertEquals(expectedUrl, request.urlString)
        assertEquals(RequestMethod.GET, request.requestMethod)
        assertEquals(expectedQueryData, request.queryData)
        assertEquals(null, request.payload)
    }

    @Test
    fun `Build Identify request with nested map of properties successfully`() {
        profile.setEmail(EMAIL)
        profile.setProperty(
            "custom_value",
            hashMapOf(
                "amount" to "2",
                "name" to "item",
                "props" to mapOf("weight" to "0.1", "diameter" to "50")
            )
        )

        withMockBase64("{\"properties\":{\"\$email\":\"$EMAIL\",\"custom_value\":{\"name\":\"item\",\"amount\":\"2\",\"props\":{\"diameter\":\"50\",\"weight\":\"0.1\"}},\"\$anonymous\":\"$ANON_ID\"},\"token\":\"$API_KEY\"}")
        val request = IdentifyRequest(apiKey = API_KEY, profile = profile)

        assertEquals(expectedUrl, request.urlString)
        assertEquals(RequestMethod.GET, request.requestMethod)
        assertEquals(expectedQueryData, request.queryData)
        assertEquals(null, request.payload)
    }

    @Test
    fun `Build Identify request with append properties successfully`() {
        profile.addAppendProperty("append_key", "value")
        profile.addAppendProperty("append_key2", "value2")

        withMockBase64("{\"properties\":{\"\$anonymous\":\"$ANON_ID\",\"\$append\":{\"append_key\":\"value\",\"append_key2\":\"value2\"}},\"token\":\"$API_KEY\"}")

        val request = IdentifyRequest(apiKey = API_KEY, profile = profile)

        assertEquals(expectedUrl, request.urlString)
        assertEquals(RequestMethod.GET, request.requestMethod)
        assertEquals(expectedQueryData, request.queryData)
        assertEquals(null, request.payload)
    }

    @Test
    fun `Append property keys overwrite previous values if re-declared`() {
        profile.addAppendProperty("append_key", "value")
        profile.addAppendProperty("append_key", "valueAgain")

        withMockBase64(expectedString = "{\"properties\":{\"\$anonymous\":\"$ANON_ID\",\"\$append\":{\"append_key\":\"valueAgain\"}},\"token\":\"$API_KEY\"}")

        val request = IdentifyRequest(apiKey = API_KEY, profile = profile)

        assertEquals(expectedQueryData, request.queryData)
    }

    @Test
    fun `Append property after request does not change existing request`() {
        profile.addAppendProperty("append_key", "value")

        withMockBase64(expectedString = "{\"properties\":{\"\$anonymous\":\"$ANON_ID\",\"\$append\":{\"append_key\":\"value\"}},\"token\":\"$API_KEY\"}")

        val request = IdentifyRequest(apiKey = API_KEY, profile = profile)
        profile.addAppendProperty("append_key_again", "value_again")

        assertEquals(expectedQueryData, request.queryData)
    }
}

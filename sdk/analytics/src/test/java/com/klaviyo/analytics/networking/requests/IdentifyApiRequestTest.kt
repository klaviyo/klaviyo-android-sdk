package com.klaviyo.analytics.networking.requests

import android.util.Base64
import com.klaviyo.analytics.model.Profile
import com.klaviyo.core_shared_tests.BaseTest
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

internal class IdentifyApiRequestTest : BaseTest() {
    private val expectedUrlPath = "api/identify"
    private val expectedMethod = RequestMethod.GET
    private val encodedQueryString = "Expected json string, base64 encoded"
    private val expectedQueryData = mapOf("data" to encodedQueryString)
    private var profile = Profile()

    override fun setup() {
        super.setup()

        // Start all tests with an empty profile and base64 mock for encoding it
        profile.setAnonymousId(ANON_ID)
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
                encodedQueryString
            } else {
                fail("Unexpected json string: \n  Expected: $expectedString \n    Actual: $str")
                ""
            }
        }
    }

    @Test
    fun `Uses the correct endpoint`() {
        val actualUrlPath = IdentifyApiRequest(profile = profile).urlPath
        assertEquals(expectedUrlPath, actualUrlPath)
    }

    @Test
    fun `Uses the correct method`() {
        val actualMethod = IdentifyApiRequest(profile = profile).method
        assertEquals(expectedMethod, actualMethod)
    }

    @Test
    fun `Query includes API key and anonymous ID`() {
        val actualQueryData = IdentifyApiRequest(profile = profile).query
        assertEquals(expectedQueryData, actualQueryData)
    }

    @Test
    fun `Does not set a payload`() {
        val actualPayload = IdentifyApiRequest(profile = profile).body
        assertNull(actualPayload)
    }

    @Test
    fun `Build Identify request successfully`() {
        val key = "custom_value"
        val value = "200"

        profile.setEmail(EMAIL)
        profile.setProperty(key, value)

        withMockBase64("{\"properties\":{\"\$email\":\"$EMAIL\",\"$key\":\"$value\",\"\$anonymous\":\"$ANON_ID\"},\"token\":\"$API_KEY\"}")

        val request = IdentifyApiRequest(profile = profile)

        assertEquals(expectedUrlPath, request.urlPath)
        assertEquals(expectedMethod, request.method)
        assertEquals(expectedQueryData, request.query)
        assertEquals(null, request.body)
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
        val request = IdentifyApiRequest(profile = profile)

        assertEquals(expectedUrlPath, request.urlPath)
        assertEquals(RequestMethod.GET, request.method)
        assertEquals(expectedQueryData, request.query)
        assertEquals(null, request.body)
    }

    @Test
    fun `Build Identify request with append properties successfully`() {
        profile.addAppendProperty("append_key", "value")
        profile.addAppendProperty("append_key2", "value2")

        withMockBase64("{\"properties\":{\"\$anonymous\":\"$ANON_ID\",\"\$append\":{\"append_key\":\"value\",\"append_key2\":\"value2\"}},\"token\":\"$API_KEY\"}")

        val request = IdentifyApiRequest(profile = profile)

        assertEquals(expectedUrlPath, request.urlPath)
        assertEquals(RequestMethod.GET, request.method)
        assertEquals(expectedQueryData, request.query)
        assertEquals(null, request.body)
    }

    @Test
    fun `Append property keys overwrite previous values if re-declared`() {
        profile.addAppendProperty("append_key", "value")
        profile.addAppendProperty("append_key", "valueAgain")

        withMockBase64(expectedString = "{\"properties\":{\"\$anonymous\":\"$ANON_ID\",\"\$append\":{\"append_key\":\"valueAgain\"}},\"token\":\"$API_KEY\"}")

        val request = IdentifyApiRequest(profile = profile)

        assertEquals(expectedQueryData, request.query)
    }

    @Test
    fun `Append property after request does not change existing request`() {
        profile.addAppendProperty("append_key", "value")

        withMockBase64(expectedString = "{\"properties\":{\"\$anonymous\":\"$ANON_ID\",\"\$append\":{\"append_key\":\"value\"}},\"token\":\"$API_KEY\"}")

        val request = IdentifyApiRequest(profile = profile)
        profile.addAppendProperty("append_key_again", "value_again")

        assertEquals(expectedQueryData, request.query)
    }
}

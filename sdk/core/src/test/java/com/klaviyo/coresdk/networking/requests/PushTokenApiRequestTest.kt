package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.BaseTest
import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.model.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Test

internal class PushTokenApiRequestTest : BaseTest() {
    private val expectedUrlPath = "api/identify"
    private val expectedMethod = RequestMethod.POST
    private var profile = Profile().setAnonymousId(ANON_ID)

    @Test
    fun `Uses the correct endpoint`() {
        assertEquals(expectedUrlPath, PushTokenApiRequest(PUSH_TOKEN, profile).urlPath)
    }

    @Test
    fun `Uses the correct method`() {
        assertEquals(expectedMethod, PushTokenApiRequest(PUSH_TOKEN, profile).method)
    }

    @Test
    fun `Does not set headers`() {
        assert(PushTokenApiRequest(PUSH_TOKEN, profile).headers.isEmpty())
    }

    @Test
    fun `Does not set a query`() {
        assert(PushTokenApiRequest(PUSH_TOKEN, profile).query.isEmpty())
    }

    @Test
    fun `Body includes only API key, profile identifiers, and push token`() {
        profile
            .setExternalId(EXTERNAL_ID)
            .setEmail(EMAIL)
            .setPhoneNumber(PHONE)
            .setProperty(ProfileKey.FIRST_NAME, "Kermit")
            .setProperty("type", "muppet")

        val body = PushTokenApiRequest(PUSH_TOKEN, profile).body
        val payload = body?.optJSONObject("data")
        val props = payload?.optJSONObject("properties")

        assertEquals(API_KEY, payload?.optString("token"))
        assertEquals(EXTERNAL_ID, props?.optString("\$external_id"))
        assertEquals(EMAIL, props?.optString("\$email"))
        assertEquals(PHONE, props?.optString("\$phone_number"))
        assertEquals(ANON_ID, props?.optString("\$anonymous"))
        assertEquals(PUSH_TOKEN, props?.optJSONObject("\$append")?.optString("\$android_tokens"))
        assertEquals(5, props?.length()) // nothing else!
    }
}

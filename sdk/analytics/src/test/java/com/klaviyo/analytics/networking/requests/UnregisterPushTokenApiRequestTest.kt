package com.klaviyo.analytics.networking.requests

import io.mockk.every
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

internal class UnregisterPushTokenApiRequestTest : BaseApiRequestTest<UnregisterPushTokenApiRequest>() {

    override val expectedUrl = "client/push-token-unregister"

    override fun makeTestRequest(): UnregisterPushTokenApiRequest =
        UnregisterPushTokenApiRequest(PUSH_TOKEN, stubProfile)

    @Test
    fun `Equality operator`() {
        val aRequest = UnregisterPushTokenApiRequest(PUSH_TOKEN, stubProfile)
        val bRequest = UnregisterPushTokenApiRequest(PUSH_TOKEN, stubProfile)
        assertEquals(aRequest, bRequest)

        val bRequestDecoded = KlaviyoApiRequestDecoder.fromJson(bRequest.toJson())
        assertEquals(aRequest, bRequestDecoded)
        assertEquals(aRequest.hashCode(), bRequestDecoded.hashCode())
    }

    @Test
    fun `JSON interoperability`() = testJsonInterop(makeTestRequest())

    @Test
    fun `Requests are equal if the token and profile are equal`() {
        val aRequest = UnregisterPushTokenApiRequest(PUSH_TOKEN, stubProfile)
        val bRequest = UnregisterPushTokenApiRequest(PUSH_TOKEN, stubProfile)
        assertEquals(aRequest, bRequest)
    }

    @Test
    fun `Requests are not equal if api key is different`() {
        val aRequest = UnregisterPushTokenApiRequest(PUSH_TOKEN, stubProfile)
        every { configMock.apiKey } returns "NEW_API_KEY"
        val bRequest = UnregisterPushTokenApiRequest(PUSH_TOKEN, stubProfile)
        assertNotEquals(aRequest, bRequest)
    }

    @Test
    fun `Requests are not equal if token is different`() {
        val aRequest = UnregisterPushTokenApiRequest(PUSH_TOKEN, stubProfile)
        val bRequest = UnregisterPushTokenApiRequest(PUSH_TOKEN.repeat(2), stubProfile)
        assertNotEquals(aRequest, bRequest)
    }

    @Test
    fun `Builds body request`() {
        val expectJson = """
            {
              "data": {
                "type": "push-token-unregister",
                "attributes": {
                  "token": "$PUSH_TOKEN",
                  "platform": "Android",
                  "vendor": "FCM",
                  "profile": {
                    "data": {
                      "type": "profile",
                      "attributes": {
                        "email": "$EMAIL",
                        "phone_number": "$PHONE",
                        "external_id": "$EXTERNAL_ID",
                        "anonymous_id": "$ANON_ID"
                      }
                    }
                  }
                }
              }
            }
        """

        val request = UnregisterPushTokenApiRequest(PUSH_TOKEN, stubProfile)
        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }
}

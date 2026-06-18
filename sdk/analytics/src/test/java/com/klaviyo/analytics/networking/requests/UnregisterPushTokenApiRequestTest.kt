package com.klaviyo.analytics.networking.requests

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

internal class UnregisterPushTokenApiRequestTest : BaseApiRequestTest<UnregisterPushTokenApiRequest>() {

    override val expectedPath = "client/push-token-unregister"

    override fun makeTestRequest(): UnregisterPushTokenApiRequest =
        UnregisterPushTokenApiRequest(API_KEY, PUSH_TOKEN, stubProfile)

    @Test
    fun `Equality operator`() {
        val aRequest = UnregisterPushTokenApiRequest(API_KEY, PUSH_TOKEN, stubProfile)
        val bRequest = UnregisterPushTokenApiRequest(API_KEY, PUSH_TOKEN, stubProfile)
        assertEquals(aRequest, bRequest)

        val bRequestDecoded = KlaviyoApiRequestDecoder.fromJson(bRequest.toJson())
        assertEquals(aRequest, bRequestDecoded)
        assertEquals(aRequest.hashCode(), bRequestDecoded.hashCode())
    }

    @Test
    fun `JSON interoperability`() = testJsonInterop(makeTestRequest())

    @Test
    fun `Requests are equal if the token and profile are equal`() {
        val aRequest = UnregisterPushTokenApiRequest(API_KEY, PUSH_TOKEN, stubProfile)
        val bRequest = UnregisterPushTokenApiRequest(API_KEY, PUSH_TOKEN, stubProfile)
        assertEquals(aRequest, bRequest)
    }

    @Test
    fun `Requests are not equal if api key is different`() {
        val aRequest = UnregisterPushTokenApiRequest(API_KEY, PUSH_TOKEN, stubProfile)
        val bRequest = UnregisterPushTokenApiRequest(API_KEY.repeat(2), PUSH_TOKEN, stubProfile)
        assertNotEquals(aRequest, bRequest)
    }

    @Test
    fun `Requests are not equal if token is different`() {
        val aRequest = UnregisterPushTokenApiRequest(API_KEY, PUSH_TOKEN, stubProfile)
        val bRequest = UnregisterPushTokenApiRequest(API_KEY, PUSH_TOKEN.repeat(2), stubProfile)
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

        val request = UnregisterPushTokenApiRequest(API_KEY, PUSH_TOKEN, stubProfile)
        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }
}

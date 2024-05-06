package com.klaviyo.analytics.networking.requests

import io.mockk.every
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

internal class PushTokenApiRequestTest : BaseApiRequestTest<PushTokenApiRequest>() {

    override val expectedUrl = "client/push-tokens"

    override fun makeTestRequest(): PushTokenApiRequest =
        PushTokenApiRequest(PUSH_TOKEN, stubProfile)

    @Test
    fun `Equality operator`() {
        val aRequest = PushTokenApiRequest(PUSH_TOKEN, stubProfile)
        val bRequest = PushTokenApiRequest(PUSH_TOKEN, stubProfile)
        assertEquals(aRequest, bRequest)

        val bRequestDecoded = KlaviyoApiRequestDecoder.fromJson(bRequest.toJson())
        assertEquals(aRequest, bRequestDecoded)
        assertEquals(aRequest.hashCode(), bRequestDecoded.hashCode())
    }

    @Test
    fun `JSON interoperability`() = testJsonInterop(makeTestRequest())

    @Test
    fun `Requests are equal if the token and profile are equal`() {
        val aRequest = PushTokenApiRequest(PUSH_TOKEN, stubProfile)
        val bRequest = PushTokenApiRequest(PUSH_TOKEN, stubProfile)
        assertEquals(aRequest, bRequest)
    }

    @Test
    fun `Requests are not equal if api key is different`() {
        val aRequest = PushTokenApiRequest(PUSH_TOKEN, stubProfile)
        every { configMock.apiKey } returns "NEW_API_KEY"
        val bRequest = PushTokenApiRequest(PUSH_TOKEN, stubProfile)
        assertNotEquals(aRequest, bRequest)
    }

    @Test
    fun `Requests are not equal if token is different`() {
        val aRequest = PushTokenApiRequest(PUSH_TOKEN, stubProfile)
        val bRequest = PushTokenApiRequest(PUSH_TOKEN.repeat(2), stubProfile)
        assertNotEquals(aRequest, bRequest)
    }

    @Test
    fun `Builds body request`() {
        val expectJson = """
            {
              "data": {
                "type": "push-token",
                "attributes": {
                  "token": "$PUSH_TOKEN",
                  "platform": "Android",
                  "vendor": "FCM",
                  "enablement_status": "AUTHORIZED",
                  "background": "AVAILABLE",
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
                  },
                  "device_metadata": {
                    "device_id": "Mock Device ID",
                    "manufacturer": "Mock Manufacturer",
                    "device_model": "Mock Model",
                    "os_name": "Android",
                    "os_version": "Mock OS Version",
                    "klaviyo_sdk": "Mock SDK",
                    "sdk_version": "Mock SDK Version",
                    "app_id": "Mock App ID",
                    "app_name": "Mock Application Label",
                    "app_version": "Mock App Version",
                    "app_build": "Mock Version Code",
                    "environment": "release"
                  }
                }
              }
            }
        """

        val request = PushTokenApiRequest(PUSH_TOKEN, stubProfile)
        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }
}

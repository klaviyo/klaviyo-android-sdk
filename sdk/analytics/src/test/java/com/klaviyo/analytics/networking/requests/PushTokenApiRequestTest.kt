package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Profile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

internal class PushTokenApiRequestTest : BaseRequestTest() {
    private val expectedUrlPath = "client/push-tokens"
    private val expectedQueryData = mapOf("company_id" to API_KEY)

    private val expectedHeaders = mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "Revision" to "2023-07-15",
        "User-Agent" to "Mock User Agent"
    )

    private val stubProfile = Profile()
        .setAnonymousId(ANON_ID)
        .setEmail(EMAIL)
        .setPhoneNumber(PHONE)
        .setExternalId(EXTERNAL_ID)

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
    fun `Uses correct endpoint`() {
        assertEquals(expectedUrlPath, PushTokenApiRequest(PUSH_TOKEN, stubProfile).urlPath)
    }

    @Test
    fun `Uses correct method`() {
        assertEquals(RequestMethod.POST, PushTokenApiRequest(PUSH_TOKEN, stubProfile).method)
    }

    @Test
    fun `Uses correct headers`() {
        assertEquals(expectedHeaders, PushTokenApiRequest(PUSH_TOKEN, stubProfile).headers)
    }

    @Test
    fun `Uses API Key in query`() {
        assertEquals(expectedQueryData, PushTokenApiRequest(PUSH_TOKEN, stubProfile).query)
    }

    @Test
    fun `JSON interoperability`() {
        val request = PushTokenApiRequest(PUSH_TOKEN, stubProfile)
        val requestJson = request.toJson()
        val revivedRequest = KlaviyoApiRequestDecoder.fromJson(requestJson)
        assert(revivedRequest is PushTokenApiRequest)
        compareJson(requestJson, revivedRequest.toJson())
    }

    @Test
    fun `Requests are equal if the token and profile are equal`() {
        val aRequest = PushTokenApiRequest(PUSH_TOKEN, stubProfile)
        val bRequest = PushTokenApiRequest(PUSH_TOKEN, stubProfile)
        assertEquals(aRequest, bRequest)
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

package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventType
import com.klaviyo.analytics.model.Profile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EventApiRequestTest : BaseRequestTest() {

    private val expectedUrlPath = "client/events/"

    private val expectedQueryData = mapOf("company_id" to API_KEY)

    private val expectedHeaders = mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "Revision" to "2023-06-06",
        "User-Agent" to "Mock User Agent"
    )

    private val stubEvent: Event = Event(EventType.CUSTOM("Test Event"))

    private val stubProfile = Profile()
        .setExternalId(EXTERNAL_ID)
        .setAnonymousId(ANON_ID)
        .setEmail(EMAIL)
        .setPhoneNumber(PHONE)

    @Test
    fun `Uses correct endpoint`() {
        assertEquals(expectedUrlPath, EventApiRequest(stubEvent, stubProfile).urlPath)
    }

    @Test
    fun `Uses correct method`() {
        assertEquals(RequestMethod.POST, EventApiRequest(stubEvent, stubProfile).method)
    }

    @Test
    fun `Uses correct headers`() {
        assertEquals(expectedHeaders, EventApiRequest(stubEvent, stubProfile).headers)
    }

    @Test
    fun `Uses API Key in query`() {
        assertEquals(expectedQueryData, EventApiRequest(stubEvent, stubProfile).query)
    }

    @Test
    fun `JSON interoperability`() {
        val request = EventApiRequest(stubEvent, stubProfile)
        val requestJson = request.toJson()
        val revivedRequest = KlaviyoApiRequestDecoder.fromJson(requestJson)
        assert(revivedRequest is EventApiRequest)
        compareJson(requestJson, revivedRequest.toJson())
    }

    private val externalId = "\$id"
    private val emailKey = "\$email"
    private val anonKey = "\$anonymous"
    private val phoneKey = "\$phone_number"

    @Test
    fun `Builds body request without properties`() {
        val expectJson = """
            {
              "data": {
                "type": "event",
                "attributes": {
                  "metric": {
                    "name": "${stubEvent.type}"
                  },
                  "profile": {
                    "$externalId": "$EXTERNAL_ID",
                    "$emailKey": "$EMAIL",
                    "$anonKey": "$ANON_ID",
                    "$phoneKey": "$PHONE"
                  },
                  "properties": {
                    "Device ID": "Mock Device ID",
                    "Device Manufacturer": "Mock Manufacturer",
                    "Device Model": "Mock Model",
                    "OS Name": "Android",
                    "OS Version": "Mock OS Version",
                    "SDK Name": "Mock SDK",
                    "SDK Version": "Mock SDK Version",
                    "App Version": "Mock App Version",
                    "App Build": "Mock Version Code",
                    "App ID": "Mock App ID",
                    "App Name": "Mock Application Label",
                    "Push Token": "Mock Push Token"
                  },
                  "time": "$ISO_TIME"
                }
              }
            }
        """

        val request = EventApiRequest(stubEvent, stubProfile)
        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }

    @Test
    fun `Builds request with properties`() {
        val expectJson = """
            {
              "data": {
                "type": "event",
                "attributes": {
                  "metric": {
                    "name": "${stubEvent.type}"
                  },
                  "profile": {
                    "$externalId": "$EXTERNAL_ID",
                    "$emailKey": "$EMAIL",
                    "$anonKey": "$ANON_ID",
                    "$phoneKey": "$PHONE"
                  },
                  "properties": {
                    "custom_value": "200",
                    "Device ID": "Mock Device ID",
                    "Device Manufacturer": "Mock Manufacturer",
                    "Device Model": "Mock Model",
                    "OS Name": "Android",
                    "OS Version": "Mock OS Version",
                    "SDK Name": "Mock SDK",
                    "SDK Version": "Mock SDK Version",
                    "App Version": "Mock App Version",
                    "App Build": "Mock Version Code",
                    "App ID": "Mock App ID",
                    "App Name": "Mock Application Label",
                    "Push Token": "Mock Push Token"
                  },
                  "time": "$ISO_TIME"
                }
              }
            }
        """

        stubEvent.setProperty("custom_value", "200")
        val request = EventApiRequest(stubEvent, stubProfile)

        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }

    @Test
    fun `Request is unaffected by changes to profile or event after the fact`() {
        val expectJson = """
            {
              "data": {
                "type": "event",
                "attributes": {
                  "metric": {
                    "name": "${stubEvent.type}"
                  },
                  "profile": {
                    "$externalId": "$EXTERNAL_ID",
                    "$emailKey": "$EMAIL",
                    "$anonKey": "$ANON_ID",
                    "$phoneKey": "$PHONE"
                  },
                  "properties": {
                    "custom_value": "200",
                    "Device ID": "Mock Device ID",
                    "Device Manufacturer": "Mock Manufacturer",
                    "Device Model": "Mock Model",
                    "OS Name": "Android",
                    "OS Version": "Mock OS Version",
                    "SDK Name": "Mock SDK",
                    "SDK Version": "Mock SDK Version",
                    "App Version": "Mock App Version",
                    "App Build": "Mock Version Code",
                    "App ID": "Mock App ID",
                    "App Name": "Mock Application Label",
                    "Push Token": "Mock Push Token"
                  },
                  "time": "$ISO_TIME"
                }
              }
            }
        """

        stubEvent.setProperty("custom_value", "200")
        val request = EventApiRequest(stubEvent, stubProfile)

        // If I mutate profile or properties after creating, it shouldn't affect the request
        stubProfile.setExternalId("ext_id")
        stubEvent.setProperty("custom_value", "100")

        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }
}

package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.BaseTest
import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.EventType
import com.klaviyo.coresdk.model.Profile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EventApiRequestTest : BaseTest() {

    private val expectedUrlPath = "client/events/"

    private val expectedQueryData = mapOf("company_id" to API_KEY)

    private val expectedHeaders = mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "Revision" to "2023-01-24"
    )

    private val stubEvent: Event = Event(EventType.CUSTOM("Test Event"))

    private val stubProfile = Profile()
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
                    "$emailKey": "$EMAIL",
                    "$anonKey": "$ANON_ID",
                    "$phoneKey": "$PHONE"
                  },
                  "properties": {},
                  "time": "$ISO_TIME"
                }
              }
            }
        """

        val request = EventApiRequest(stubEvent, stubProfile)
        compareJson(JSONObject(expectJson), JSONObject(request.formatBody()!!))
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
                    "$emailKey": "$EMAIL",
                    "$anonKey": "$ANON_ID",
                    "$phoneKey": "$PHONE"
                  },
                  "properties": {
                    "custom_value": "200"
                  },
                  "time": "$ISO_TIME"
                }
              }
            }
        """

        stubEvent.setProperty("custom_value", "200")
        val request = EventApiRequest(stubEvent, stubProfile)

        compareJson(JSONObject(expectJson), JSONObject(request.formatBody()!!))
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
                    "$emailKey": "$EMAIL",
                    "$anonKey": "$ANON_ID",
                    "$phoneKey": "$PHONE"
                  },
                  "properties": {
                    "custom_value": "200"
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

        compareJson(JSONObject(expectJson), JSONObject(request.formatBody()!!))
    }
}

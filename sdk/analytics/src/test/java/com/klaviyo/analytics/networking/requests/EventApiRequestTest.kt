package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventMetric
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

internal class EventApiRequestTest : BaseApiRequestTest<EventApiRequest>() {

    override val expectedUrl = "client/events/"

    private val stubEvent: Event = Event(EventMetric.CUSTOM("Test Event"))

    override fun makeTestRequest(): EventApiRequest =
        EventApiRequest(stubEvent, stubProfile)

    @Before
    override fun setup() {
        super.setup()
        mockkObject(Klaviyo)
        every { Klaviyo.getPushToken() } returns PUSH_TOKEN
    }

    @After
    override fun cleanup() {
        super.cleanup()
        unmockkObject(Klaviyo)
    }

    @Test
    fun `JSON interoperability`() = testJsonInterop(makeTestRequest())

    @Test
    fun `Builds body request without properties`() {
        val expectJson = """
            {
              "data": {
                "type": "event",
                "attributes": {
                  "metric": {
                    "data": {
                      "type": "metric",
                      "attributes": {
                        "name": "${stubEvent.metric}"
                      }
                    }
                  },
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
                    "Push Token": "$PUSH_TOKEN"
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
                    "data": {
                      "type": "metric",
                      "attributes": {
                        "name": "${stubEvent.metric}"
                      }
                    }
                  },
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
                    "Push Token": "$PUSH_TOKEN"
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
                    "data": {
                      "type": "metric",
                      "attributes": {
                        "name": "${stubEvent.metric}"
                      }
                    }
                  },
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
                    "Push Token": "$PUSH_TOKEN"
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

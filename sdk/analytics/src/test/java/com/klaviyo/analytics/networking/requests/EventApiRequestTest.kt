package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.fixtures.mockDeviceProperties
import com.klaviyo.fixtures.unmockDeviceProperties
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import java.util.UUID
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

internal class EventApiRequestTest : BaseApiRequestTest<EventApiRequest>() {

    override val expectedPath = "client/events"

    private val stubEvent: Event = Event(EventMetric.CUSTOM("Test Event"))
        .setUniqueId("uuid")
        .setValue(12.34)

    override fun makeTestRequest(): EventApiRequest =
        EventApiRequest(stubEvent, stubProfile)

    @Before
    override fun setup() {
        super.setup()
        mockkObject(Klaviyo)
        every { Klaviyo.getPushToken() } returns PUSH_TOKEN
        mockDeviceProperties()
    }

    @After
    override fun cleanup() {
        super.cleanup()
        unmockkObject(Klaviyo)
        unmockDeviceProperties()
    }

    @Test
    fun `JSON interoperability`() = testJsonInterop(makeTestRequest())

    @Test
    fun `Builds body request without properties`() {
        // Note: Including $value and $event_id was an oversight when we first migrated to V3 APIs.
        // Now we need to leave it in for backwards compatibility (the APIs may be updated in the
        // future to filter out all reserved keys, but we'll leave it in the SDKs for now).
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
                    "Push Token": "$PUSH_TOKEN",
                    "${EventKey.VALUE}": 12.34,
                    "${EventKey.EVENT_ID}": "uuid"
                  },
                  "time": "$ISO_TIME",
                  "value": 12.34,
                  "unique_id": "uuid"
                }
              }
            }
        """

        val request = EventApiRequest(stubEvent, stubProfile)
        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }

    @Test
    fun `Builds request with properties`() {
        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "00000000-0000-0000-0000-00000000abcd"

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
                    "Push Token": "$PUSH_TOKEN",
                    "${EventKey.VALUE}": 12.34,
                  },
                  "time": "$ISO_TIME",
                  "value": 12.34,
                  "unique_id": "00000000-0000-0000-0000-00000000abcd"
                }
              }
            }
        """

        val thisStubEvent = stubEvent.copy()
            .setUniqueId(null)
            .setProperty("custom_value", "200")

        val request = EventApiRequest(thisStubEvent, stubProfile)

        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))

        unmockkStatic(UUID::class)
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
                    "Push Token": "$PUSH_TOKEN",
                    "${EventKey.VALUE}": 12.34,
                    "${EventKey.EVENT_ID}": "uuid"
                  },
                  "time": "$ISO_TIME",
                  "value": 12.34,
                  "unique_id": "uuid"
                }
              }
            }
        """

        stubEvent.setProperty("custom_value", "200")
        val origEvent = stubEvent.copy()
        val request = EventApiRequest(stubEvent, stubProfile)

        // Event was not mutated by creating the API request
        compareJson(JSONObject(origEvent.toString()), JSONObject(stubEvent.toString()))

        // If I mutate profile or properties after creating, it shouldn't affect the request
        stubProfile.setExternalId("ext_id")
        stubEvent.setProperty("custom_value", "100")

        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }
}

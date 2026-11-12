package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.core.Constants
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        mockkStatic(Klaviyo::class)
        mockkObject(Klaviyo)
        every { Klaviyo.getPushToken() } returns PUSH_TOKEN
        mockDeviceProperties()
    }

    @After
    override fun cleanup() {
        super.cleanup()
        unmockkObject(Klaviyo)
        unmockkStatic(Klaviyo::class)
        unmockDeviceProperties()
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
                    "Push Token": "$PUSH_TOKEN"
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
                    "Push Token": "$PUSH_TOKEN"
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

    @Test
    fun `Builds body request with value_currency`() {
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
                  "time": "$ISO_TIME",
                  "value": 12.34,
                  "value_currency": "USD",
                  "unique_id": "uuid"
                }
              }
            }
        """

        val request = EventApiRequest(stubEvent.copy().setValueCurrency("USD"), stubProfile)
        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }

    @Test
    fun `Omits value_currency when it is unset`() {
        val attributes = JSONObject(EventApiRequest(stubEvent, stubProfile).requestBody!!)
            .getJSONObject("data")
            .getJSONObject("attributes")

        assertNull(stubEvent.valueCurrency)
        assertFalse(attributes.has("value_currency"))
        assertFalse(attributes.getJSONObject("properties").has(EventKey.VALUE_CURRENCY.name))
    }

    @Test
    fun `Sends reserved keys as top-level attributes only`() {
        val event = stubEvent.copy().setValueCurrency("USD")
        val attributes = JSONObject(EventApiRequest(event, stubProfile).requestBody!!)
            .getJSONObject("data")
            .getJSONObject("attributes")
        val properties = attributes.getJSONObject("properties")

        assertEquals(12.34, attributes.getDouble("value"), 0.0)
        assertEquals("USD", attributes.getString("value_currency"))
        assertEquals("uuid", attributes.getString("unique_id"))
        assertFalse(properties.has(EventKey.VALUE.name))
        assertFalse(properties.has(EventKey.VALUE_CURRENCY.name))
        assertFalse(properties.has(EventKey.EVENT_ID.name))

        // The caller's event still carries the reserved keys
        assertEquals(12.34, event.value)
        assertEquals("USD", event.valueCurrency)
        assertEquals("uuid", event.uniqueId)
    }

    @Test
    fun `Does not include the SDK features header even when the flags that would trigger it on PushTokenApiRequest are set`() {
        every { mockConfig.hasManifestKey(Constants.AUTOMATIC_PUSH_OPEN_TRACKING) } returns true
        every { mockConfig.hasManifestKey(Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING) } returns true
        every { mockConfig.getManifestBoolean(Constants.AUTOMATIC_PUSH_OPEN_TRACKING, false) } returns true
        every { mockConfig.getManifestBoolean(Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING, false) } returns true
        val request = EventApiRequest(stubEvent, stubProfile)
        assertNull(request.headers["X-Klaviyo-Sdk-Features"])
    }
}

package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class NativeBridgeMessageTest : BaseTest() {

    @Before
    override fun setup() {
        super.setup()
    }

    @After
    override fun cleanup() {
        super.cleanup()
    }

    @Test
    fun `test decodeWebviewMessage with unrecognized message type throws exception`() {
        // Setup
        val unrecognizedMessage = "{\"type\": \"javascript is for clowns\", \"data\": {}}"

        // Act & Assert
        assertThrows(IllegalStateException::class.java) {
            NativeBridgeMessage.decodeWebviewMessage(unrecognizedMessage)
        }
    }

    @Test
    fun `test decodeWebviewMessage properly decodes show type`() {
        val showMessage = """
            {"type": "formWillAppear", "data": {"formId": "abc123", "formName": "Test Form"}}
        """.trimIndent()

        val result = NativeBridgeMessage.decodeWebviewMessage(showMessage)

        assert(result is NativeBridgeMessage.FormWillAppear)
        assertEquals("abc123", (result as NativeBridgeMessage.FormWillAppear).formId)
        assertEquals("Test Form", result.formName)
    }

    @Test
    fun `formWillAppear with missing formId parses with empty default`() {
        val message = """{"type": "formWillAppear", "data": {"formName": "Test"}}"""
        val result = NativeBridgeMessage.decodeWebviewMessage(message) as NativeBridgeMessage.FormWillAppear
        assertEquals("", result.formId)
        assertEquals("Test", result.formName)
    }

    @Test
    fun `formWillAppear with missing formName parses with empty default`() {
        val message = """{"type": "formWillAppear", "data": {"formId": "abc123"}}"""
        val result = NativeBridgeMessage.decodeWebviewMessage(message) as NativeBridgeMessage.FormWillAppear
        assertEquals("abc123", result.formId)
        assertEquals("", result.formName)
    }

    @Test
    fun `test decodeWebviewMessage decodes formName from formWillAppear`() {
        val showMessage = """
            {"type": "formWillAppear", "data": {"formId": "abc123", "formName": "My Newsletter"}}
        """.trimIndent()

        val result = NativeBridgeMessage.decodeWebviewMessage(showMessage) as NativeBridgeMessage.FormWillAppear

        assertEquals("abc123", result.formId)
        assertEquals("My Newsletter", result.formName)
    }

    @Test
    fun `formWillAppear with empty formName parses with empty string`() {
        val message = """{"type": "formWillAppear", "data": {"formId": "abc123", "formName": ""}}"""
        val result = NativeBridgeMessage.decodeWebviewMessage(message) as NativeBridgeMessage.FormWillAppear
        assertEquals("abc123", result.formId)
        assertEquals("", result.formName)
    }

    @Test
    fun `test decodeWebviewMessage properly decodes close type`() {
        val closeMessage = """{"type": "formDisappeared", "data": {"formId": "abc123", "formName": "Test Form"}}"""

        val result = NativeBridgeMessage.decodeWebviewMessage(closeMessage)

        assert(result is NativeBridgeMessage.FormDisappeared)
        assertEquals("abc123", (result as NativeBridgeMessage.FormDisappeared).formId)
        assertEquals("Test Form", result.formName)
    }

    @Test
    fun `formDisappeared with missing formId still parses with empty default`() {
        val message = """{"type": "formDisappeared", "data": {"formName": "Test"}}"""
        val result = NativeBridgeMessage.decodeWebviewMessage(message) as NativeBridgeMessage.FormDisappeared
        assertEquals("", result.formId)
        assertEquals("Test", result.formName)
    }

    @Test
    fun `test decodeWebviewMessage decodes formName from formDisappeared`() {
        val closeMessage = """
            {"type": "formDisappeared", "data": {"formId": "abc123", "formName": "My Newsletter"}}
        """.trimIndent()

        val result = NativeBridgeMessage.decodeWebviewMessage(closeMessage) as NativeBridgeMessage.FormDisappeared

        assertEquals("abc123", result.formId)
        assertEquals("My Newsletter", result.formName)
    }

    @Test
    fun `test decodeWebviewMessage errors when profile event has no event name`() {
        // Setup
        val eventMessage = "{\"type\": \"profileEvent\", \"data\": {\"properties\": {}}}"
        every { Registry.log.error(any(), any<Throwable>()) } just Runs

        // Act & Assert
        assertThrows(IllegalStateException::class.java) {
            NativeBridgeMessage.decodeWebviewMessage(eventMessage)
        }
    }

    @Test
    fun `test decodeWebviewMessage success profile event`() {
        // Setup
        val eventMessage = """
           {
              "type": "trackProfileEvent",
              "data": {
                "metric": "Form completed by profile",
                "properties": {}
              }
           } 
        """.trimIndent()
        every { Registry.log.error(any(), any<Throwable>()) } just Runs

        val decoded = NativeBridgeMessage.decodeWebviewMessage(eventMessage) as NativeBridgeMessage.TrackProfileEvent
        val expectedMetric = EventMetric.CUSTOM("Form completed by profile")
        assertEquals(expectedMetric, decoded.event.metric)
    }

    @Test
    fun `test decodeWebviewMessage successfully parses profile event with properties`() {
        val eventMessage = """
           {
              "type": "trackProfileEvent",
              "data": {
                "metric": "Form completed by profile",
                "properties": {
                  "key1": "value1",
                  "key2": "value2"
                }
              }
           } 
        """.trimIndent()
        every { Registry.log.error(any(), any<Throwable>()) } just Runs

        val result = NativeBridgeMessage.decodeWebviewMessage(eventMessage) as NativeBridgeMessage.TrackProfileEvent

        // Assert
        assertEquals(2, result.event.propertyCount())
        assertEquals("value1", result.event[EventKey.CUSTOM("key1")])
        assertEquals("value2", result.event[EventKey.CUSTOM("key2")])
    }

    @Test
    fun `test getProperties logs error on exception`() {
        val eventMessage = """
           {
              "type": "trackProfileEvent",
              "data": {
                "metric": "Form completed by profile",
                "properties": {
                  "key1": "value1",
                  "key2": 2
                }
              }
           } 
        """.trimIndent()
        every { Registry.log.error(any(), any<Throwable>()) } just Runs

        val result = NativeBridgeMessage.decodeWebviewMessage(eventMessage) as NativeBridgeMessage.TrackProfileEvent

        // Assert
        assertEquals("value1", result.event[EventKey.CUSTOM("key1")])
        assertEquals(2, result.event[EventKey.CUSTOM("key2")])
    }

    @Test
    fun `test aggregate event`() {
        // Setup
        val aggregateMessage = """
            {
              "type": "trackAggregateEvent",
              "data": {
                "metric_group": "signup-forms",
                "events": [
                  {
                    "metric": "stepSubmit",
                    "log_to_statsd": true,
                    "log_to_s3": true,
                    "log_to_metrics_service": true,
                    "metric_service_event_name": "submitted_form_step",
                    "event_details": {
                      "form_version_c_id": "1",
                      "is_client": true,
                      "submitted_fields": {
                        "source": "Local Form",
                        "email": "local@local.com",
                        "consent_method": "Klaviyo Form",
                        "consent_form_id": "64CjgW",
                        "consent_form_version": 3,
                        "sent_identifiers": {},
                        "sms_consent": true,
                        "step_name": "Email Opt-In"
                      },
                      "step_name": "Email Opt-In",
                      "step_number": 1,
                      "action_type": "Submit Step",
                      "form_id": "64CjgW",
                      "form_version_id": 3,
                      "form_type": "POPUP",
                      "device_type": "DESKTOP",
                      "hostname": "localhost",
                      "href": "http://localhost:4001/onsite/js/",
                      "page_url": "http://localhost:4001/onsite/js/",
                      "first_referrer": "http://localhost:4001/onsite/js/",
                      "referrer": "http://localhost:4001/onsite/js/",
                      "cid": "ODZjYjJmMjUtNjliMC00ZGVlLTllM2YtNDY5YTlmNjcwYmUz"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val expectedAggBody =
            JSONObject(
                "{\"metric_group\":\"signup-forms\",\"events\":[{\"log_to_metrics_service\":true,\"metric\":\"stepSubmit\",\"log_to_statsd\":true,\"event_details\":{\"page_url\":\"http://localhost:4001/onsite/js/\",\"first_referrer\":\"http://localhost:4001/onsite/js/\",\"action_type\":\"Submit Step\",\"form_version_id\":3,\"form_id\":\"64CjgW\",\"device_type\":\"DESKTOP\",\"form_type\":\"POPUP\",\"referrer\":\"http://localhost:4001/onsite/js/\",\"submitted_fields\":{\"sms_consent\":true,\"consent_method\":\"Klaviyo Form\",\"consent_form_version\":3,\"step_name\":\"Email Opt-In\",\"consent_form_id\":\"64CjgW\",\"source\":\"Local Form\",\"email\":\"local@local.com\",\"sent_identifiers\":{}},\"hostname\":\"localhost\",\"step_number\":1,\"form_version_c_id\":\"1\",\"step_name\":\"Email Opt-In\",\"is_client\":true,\"href\":\"http://localhost:4001/onsite/js/\",\"cid\":\"ODZjYjJmMjUtNjliMC00ZGVlLTllM2YtNDY5YTlmNjcwYmUz\"},\"metric_service_event_name\":\"submitted_form_step\",\"log_to_s3\":true}]}"
            )
        // Act
        val result =
            NativeBridgeMessage.decodeWebviewMessage(aggregateMessage) as NativeBridgeMessage.TrackAggregateEvent

        // Assert
        assertEquals(expectedAggBody.toString(), result.payload.toString())
    }

    @Test
    fun `test deeplinks decoding`() {
        val deeplinkMessage = """
            {
              "type": "openDeepLink",
              "data": {
                "ios": "klaviyotest://settings",
                "android": "klaviyotest://settings",
                "formId": "abc123",
                "formName": "Test Form",
                "buttonLabel": "Click Me"
              }
            }
        """.trimIndent()

        val result = NativeBridgeMessage.decodeWebviewMessage(deeplinkMessage) as NativeBridgeMessage.OpenDeepLink

        assertEquals("klaviyotest://settings", result.route)
        assertEquals("abc123", result.formId)
        assertEquals("Test Form", result.formName)
        assertEquals("Click Me", result.buttonLabel)
    }

    @Test
    fun `deeplink without metadata fields parses with empty defaults`() {
        val deeplinkMessage = """
            {
              "type": "openDeepLink",
              "data": {
                "ios": "klaviyotest://settings"
              }
            }
        """.trimIndent()

        val result = NativeBridgeMessage.decodeWebviewMessage(deeplinkMessage) as NativeBridgeMessage.OpenDeepLink
        assertNull(result.route)
        assertEquals("", result.formId)
        assertEquals("", result.formName)
        assertEquals("", result.buttonLabel)
    }

    @Test
    fun `deeplink with empty android field returns OpenDeepLink with null route`() {
        val deeplinkMessage = """
            {
              "type": "openDeepLink",
              "data": {
                "ios": "klaviyotest://settings",
                "android": "",
                "formId": "abc123",
                "formName": "Test Form",
                "buttonLabel": "Click Me"
              }
            }
        """.trimIndent()

        val result = NativeBridgeMessage.decodeWebviewMessage(deeplinkMessage) as NativeBridgeMessage.OpenDeepLink

        assertEquals(null, result.route)
        assertEquals("abc123", result.formId)
        assertEquals("Test Form", result.formName)
        assertEquals("Click Me", result.buttonLabel)
    }

    @Test
    fun `openDeepLink with missing buttonLabel parses with empty default`() {
        val message = """
            {
              "type": "openDeepLink",
              "data": {
                "android": "klaviyotest://settings",
                "formId": "abc123",
                "formName": "My Newsletter"
              }
            }
        """.trimIndent()

        val result = NativeBridgeMessage.decodeWebviewMessage(message) as NativeBridgeMessage.OpenDeepLink
        assertEquals("klaviyotest://settings", result.route)
        assertEquals("abc123", result.formId)
        assertEquals("My Newsletter", result.formName)
        assertEquals("", result.buttonLabel)
    }

    @Test
    fun `test decodeWebviewMessage decodes formId and formName from openDeepLink`() {
        val deeplinkMessage = """
            {
              "type": "openDeepLink",
              "data": {
                "android": "klaviyotest://settings",
                "formId": "abc123",
                "formName": "My Newsletter",
                "buttonLabel": "Shop Now"
              }
            }
        """.trimIndent()

        val result = NativeBridgeMessage.decodeWebviewMessage(deeplinkMessage) as NativeBridgeMessage.OpenDeepLink

        assertEquals("klaviyotest://settings", result.route)
        assertEquals("abc123", result.formId)
        assertEquals("My Newsletter", result.formName)
        assertEquals("Shop Now", result.buttonLabel)
    }

    @Test
    fun `abort message parses a reason, or falls back on unknown`() {
        val deeplinkMessage = """
            {
              "type": "abort",
              "data": {
                "reason": "test"
              }
            }
        """.trimIndent()

        var result = NativeBridgeMessage.decodeWebviewMessage(deeplinkMessage) as NativeBridgeMessage.Abort

        assertEquals("test", result.reason)

        val deeplinkMessageWithoutReason = """
            {
              "type": "abort",
              "data": {}
            }
        """.trimIndent()

        result = NativeBridgeMessage.decodeWebviewMessage(deeplinkMessageWithoutReason) as NativeBridgeMessage.Abort

        assertEquals("Unknown", result.reason)
    }

    @Test
    fun `handshake field sends proper type`() {
        val deeplinkMessage = """
            {
              "type": "handShook",
              "data": {
              }
            }
        """.trimIndent()
        assertEquals(
            NativeBridgeMessage.HandShook,
            NativeBridgeMessage.decodeWebviewMessage(deeplinkMessage)
        )
    }

    @Test
    fun `Verify IAF handshake`() {
        assertEquals(
            """
                [
                  {
                    "type": "handShook",
                    "version": 1
                  },
                  {
                    "type": "formWillAppear",
                    "version": 1
                  },
                  {
                    "type": "trackAggregateEvent",
                    "version": 1
                  },
                  {
                    "type": "trackProfileEvent",
                    "version": 1
                  },
                  {
                    "type": "openDeepLink",
                    "version": 2
                  },
                  {
                    "type": "formDisappeared",
                    "version": 1
                  },
                  {
                    "type": "abort",
                    "version": 1
                  }
                ]
            """.replace("\\s".toRegex(), ""),
            NativeBridgeMessage.handShakeData.compileJson()
        )
    }
}

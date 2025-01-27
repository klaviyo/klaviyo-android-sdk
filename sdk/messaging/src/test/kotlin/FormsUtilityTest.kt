package com.klaviyo.messaging

import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows

class FormsUtilityTest : BaseTest() {

    @Before
    override fun setup() {
        super.setup()
    }

    @After
    override fun cleanup() {
        super.cleanup()
    }

    @Test
    fun `test getProperties successfully parses properties`() {
        // Setup
        val json = JSONObject()
        val properties = JSONObject()
        properties.put("key1", "value1")
        properties.put("key2", "value2")
        json.put("properties", properties)

        // Act
        val result = json.getProperties()

        // Assert
        assertEquals(2, result.size)
        assertEquals("value1", result[EventKey.CUSTOM("key1")])
        assertEquals("value2", result[EventKey.CUSTOM("key2")])
    }

    @Test
    fun `test getProperties logs error on exception`() {
        // Setup
        val json = JSONObject("{\"properties\": {\"key1\": 123}}") // Non-string value
        every { Registry.log.error(any(), any<Throwable>()) } just Runs

        // Act
        json.getProperties()

        // Assert
        verify { Registry.log.error(any(), any<Exception>()) }
    }

    @Test
    fun `test decodeWebviewMessage with unrecognized message type throws exception`() {
        // Setup
        val unrecognizedMessage = "{\"type\": \"javascript is for clowns\", \"data\": {}}"

        // Act & Assert
        assertThrows(IllegalStateException::class.java) {
            decodeWebviewMessage(unrecognizedMessage)
        }
    }

    @Test
    fun `test decodeWebviewMessage properly decodes show type`() {
        // Setup
        val showMessage = "{\"type\": \"formDidAppear\", \"data\": {}}"

        // Act
        val result = decodeWebviewMessage(showMessage)

        // Assert
        assertEquals(KlaviyoWebFormMessageType.Show, result)
    }

    @Test
    fun `test decodeWebviewMessage properly decodes close type`() {
        // Setup
        val closeMessage = "{\"type\": \"formDidClose\", \"data\": {}}"

        // Act
        val result = decodeWebviewMessage(closeMessage)

        // Assert
        assertEquals(KlaviyoWebFormMessageType.Close, result)
    }

    @Test
    fun `test decodeWebviewMessage errors when profile event has no event name`() {
        // Setup
        val eventMessage = "{\"type\": \"profileEvent\", \"data\": {\"properties\": {}}}"
        every { Registry.log.error(any(), any<Throwable>()) } just Runs

        // Act & Assert
        assertThrows(IllegalStateException::class.java) {
            decodeWebviewMessage(eventMessage)
        }
    }

    @Test
    fun `test decodeWebviewMessage success profile event`() {
        // Setup
        val eventMessage = "{\"type\": \"profileEventTracked\", \"data\": {\"metric\": \"Form completed by profile\", \"properties\": {}}}"
        every { Registry.log.error(any(), any<Throwable>()) } just Runs

        val decoded = decodeWebviewMessage(eventMessage) as KlaviyoWebFormMessageType.ProfileEvent
        val expectedMetric = EventMetric.CUSTOM("Form completed by profile")
        assertEquals(expectedMetric, decoded.event.metric)
    }

    @Test
    fun `test aggregate event`() {
        // Setup
        val aggregateMessage = """
            {
              "type": "aggregateEventTracked",
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
            JSONObject("{\"metric_group\":\"signup-forms\",\"events\":[{\"log_to_metrics_service\":true,\"metric\":\"stepSubmit\",\"log_to_statsd\":true,\"event_details\":{\"page_url\":\"http://localhost:4001/onsite/js/\",\"first_referrer\":\"http://localhost:4001/onsite/js/\",\"action_type\":\"Submit Step\",\"form_version_id\":3,\"form_id\":\"64CjgW\",\"device_type\":\"DESKTOP\",\"form_type\":\"POPUP\",\"referrer\":\"http://localhost:4001/onsite/js/\",\"submitted_fields\":{\"sms_consent\":true,\"consent_method\":\"Klaviyo Form\",\"consent_form_version\":3,\"step_name\":\"Email Opt-In\",\"consent_form_id\":\"64CjgW\",\"source\":\"Local Form\",\"email\":\"local@local.com\",\"sent_identifiers\":{}},\"hostname\":\"localhost\",\"step_number\":1,\"form_version_c_id\":\"1\",\"step_name\":\"Email Opt-In\",\"is_client\":true,\"href\":\"http://localhost:4001/onsite/js/\",\"cid\":\"ODZjYjJmMjUtNjliMC00ZGVlLTllM2YtNDY5YTlmNjcwYmUz\"},\"metric_service_event_name\":\"submitted_form_step\",\"log_to_s3\":true}]}")
        // Act
        val result =
            decodeWebviewMessage(aggregateMessage) as KlaviyoWebFormMessageType.AggregateEventTracked

        // Assert
        assertEquals(expectedAggBody.toString(), result.payload.toString())
    }
}
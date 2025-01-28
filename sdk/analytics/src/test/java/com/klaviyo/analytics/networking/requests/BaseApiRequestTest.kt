package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.DevicePropertiesTest
import com.klaviyo.analytics.model.Profile
import com.klaviyo.fixtures.BaseTest
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test

internal abstract class BaseApiRequestTest<T> : BaseTest() where T : KlaviyoApiRequest {

    abstract val expectedUrl: String

    open val expectedMethod = RequestMethod.POST

    open val expectedHeaders = mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "Revision" to "1234-56-78",
        "User-Agent" to "Mock User Agent",
        "X-Klaviyo-Mobile" to "1",
        "X-Klaviyo-Attempt-Count" to "0/50" // Note: 0/50 is just the default, it increments to 1/50 before a real send!
    )

    open val expectedQuery = mapOf("company_id" to API_KEY)

    open val stubProfile = Profile()
        .setExternalId(EXTERNAL_ID)
        .setAnonymousId(ANON_ID)
        .setEmail(EMAIL)
        .setPhoneNumber(PHONE)

    open val stubAggregateEventPayload = JSONObject(
        """
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
    )

    abstract fun makeTestRequest(): T

    @Before
    override fun setup() {
        super.setup()
        DevicePropertiesTest.mockDeviceProperties()
    }

    @Test
    fun `Uses expected URL`() {
        Assert.assertEquals(expectedUrl, makeTestRequest().urlPath)
    }

    @Test
    fun `Uses expected HTTP method`() {
        Assert.assertEquals(expectedMethod, makeTestRequest().method)
    }

    @Test
    fun `Uses expected HTTP headers`() {
        Assert.assertEquals(expectedHeaders, makeTestRequest().headers)
    }

    @Test
    fun `Uses expected URL query`() {
        Assert.assertEquals(expectedQuery, makeTestRequest().query)
    }

    inline fun <reified T> testJsonInterop(request: T) where T : KlaviyoApiRequest {
        val requestJson = request.toJson()
        val revivedRequest = KlaviyoApiRequestDecoder.fromJson(requestJson)
        assert(revivedRequest is T)
        compareJson(requestJson, revivedRequest.toJson())
    }
}

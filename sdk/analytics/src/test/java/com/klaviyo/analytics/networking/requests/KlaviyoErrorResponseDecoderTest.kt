package com.klaviyo.analytics.networking.requests

import com.klaviyo.fixtures.BaseTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class KlaviyoErrorResponseDecoderTest : BaseTest() {

    @Test
    fun `Builds error response`() {
        val errorResponse = KlaviyoErrorResponse(
            errors = listOf(
                KlaviyoError(
                    id = "67ed6dbf-1653-499b-a11d-30310aa01ff7",
                    status = 400,
                    title = "Invalid input.",
                    detail = "Invalid phone number format (Example of a valid format: +12345678901)",
                    source = KlaviyoErrorSource(
                        pointer = "/data/attributes/phone_number"
                    )
                )
            )
        )
        val errorJson = """
            {
              "errors": [
                {
                  "id": "67ed6dbf-1653-499b-a11d-30310aa01ff7",
                  "status": 400,
                  "code": "invalid",
                  "title": "Invalid input.",
                  "detail": "Invalid phone number format (Example of a valid format: +12345678901)",
                  "source": {
                    "pointer": "/data/attributes/phone_number"
                  },
                  "links": {},
                  "meta": {}
                }
              ]
            }
        """.trimIndent()
        assertEquals(errorResponse, KlaviyoErrorResponseDecoder.fromJson(JSONObject(errorJson)))
    }

    @Test
    fun `Builds error response with some spooky nulls`() {
        val errorResponse = KlaviyoErrorResponse(
            errors = listOf(
                KlaviyoError(
                    id = "67ed6dbf-1653-499b-a11d-30310aa01ff7",
                    status = -1,
                    title = "Invalid input.",
                    source = KlaviyoErrorSource()
                )
            )
        )
        val errorJson = """
            {
              "errors": [
                {
                  "id": "67ed6dbf-1653-499b-a11d-30310aa01ff7",
                  "status": -1,
                  "code": null,
                  "title": "Invalid input.",
                  "detail": null,
                  "source": {
                    "pointer": null
                  },
                  "links": {},
                  "meta": {}
                }
              ]
            }
        """.trimIndent()
        assertEquals(errorResponse, KlaviyoErrorResponseDecoder.fromJson(JSONObject(errorJson)))
    }

    @Test
    fun `Build error response with an empty list of errors`() {
        val errorResponse = KlaviyoErrorResponse(
            errors = listOf()
        )
        val errorJson = """
            {
              "errors": []
            }
        """.trimIndent()
        assertEquals(errorResponse, KlaviyoErrorResponseDecoder.fromJson(JSONObject(errorJson)))
    }

    @Test
    fun `Build error response with an multiple errors`() {
        val errorResponse = KlaviyoErrorResponse(
            errors = listOf(
                KlaviyoError(
                    id = "67ed6dbf-1653-499b-a11d-30310aa01ff7",
                    status = -1,
                    title = "Invalid input.",
                    source = KlaviyoErrorSource()
                ),
                KlaviyoError(
                    id = "123456",
                    status = 800,
                    title = "Invalid input.",
                    source = KlaviyoErrorSource()
                )
            )
        )
        val errorJson = """
            {
              "errors": [
              {
                  "id": "67ed6dbf-1653-499b-a11d-30310aa01ff7",
                  "status": -1,
                  "code": null,
                  "title": "Invalid input.",
                  "detail": null,
                  "source": {
                    "pointer": null
                  },
                  "links": {},
                  "meta": {}
                },
                {
                  "id": "123456",
                  "status": 800,
                  "code": null,
                  "title": "Invalid input.",
                  "detail": null,
                  "source": {
                    "pointer": null
                  },
                  "links": {},
                  "meta": {}
                }
              ]
            }
        """.trimIndent()
        assertEquals(errorResponse, KlaviyoErrorResponseDecoder.fromJson(JSONObject(errorJson)))
    }
}

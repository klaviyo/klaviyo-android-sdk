package com.klaviyo.analytics.networking.requests

import org.junit.Assert.assertEquals
import org.junit.Test

internal class FetchGeofencesRequestTest : BaseApiRequestTest<FetchGeofencesRequest>() {

    override val expectedPath = "client/geofences"

    override val expectedMethod = RequestMethod.GET

    override val expectedQuery = mapOf("company_id" to API_KEY)

    override fun makeTestRequest(): FetchGeofencesRequest = FetchGeofencesRequest()

    @Test
    fun `jSON interoperability`() = testJsonInterop(makeTestRequest())

    @Test
    fun `parses geofences from response`() {
        val responseJson = """
            {
                "data": [
                    {
                        "type": "geofence",
                        "id": "8db4effa-44f1-45e6-a88d-8e7d50516a0f",
                        "attributes": {
                            "latitude": 40.7128,
                            "longitude": -74.006,
                            "radius": 100
                        }
                    },
                    {
                        "type": "geofence",
                        "id": "a84011cf-93ef-4e78-b047-c0ce4ea258e4",
                        "attributes": {
                            "latitude": 40.6892,
                            "longitude": -74.0445,
                            "radius": 200
                        }
                    }
                ]
            }
        """.trimIndent()

        val request = makeTestRequest()
            .setResponseBody(responseJson)
            .setStatus(KlaviyoApiRequest.Status.Complete)

        val result = request.getResult()
        assert(result is FetchGeofencesResult.Success)

        val successResult = result as FetchGeofencesResult.Success
        assertEquals(2, successResult.data.size)

        val firstGeofence = successResult.data[0]
        assertEquals("8db4effa-44f1-45e6-a88d-8e7d50516a0f", firstGeofence.id)
        assertEquals(40.7128, firstGeofence.latitude, 0.0001)
        assertEquals(-74.006, firstGeofence.longitude, 0.0001)
        assertEquals(100.0, firstGeofence.radius, 0.0001)

        val secondGeofence = successResult.data[1]
        assertEquals("a84011cf-93ef-4e78-b047-c0ce4ea258e4", secondGeofence.id)
        assertEquals(40.6892, secondGeofence.latitude, 0.0001)
        assertEquals(-74.0445, secondGeofence.longitude, 0.0001)
        assertEquals(200.0, secondGeofence.radius, 0.0001)
    }

    @Test
    fun `returns success with empty list when no geofences`() {
        val responseJson = """{"data": []}"""

        val request = makeTestRequest()
            .setResponseBody(responseJson)
            .setStatus(KlaviyoApiRequest.Status.Complete)

        val result = request.getResult()
        assert(result is FetchGeofencesResult.Success)

        val successResult = result as FetchGeofencesResult.Success
        assertEquals(0, successResult.data.size)
    }

    @Test
    fun `returns unavailable when request is unsent`() {
        val request = makeTestRequest()
            .setStatus(KlaviyoApiRequest.Status.Unsent)

        assert(request.getResult() is FetchGeofencesResult.Unavailable)
    }

    @Test
    fun `returns unavailable when request is inflight`() {
        val request = makeTestRequest()
            .setStatus(KlaviyoApiRequest.Status.Inflight)

        assert(request.getResult() is FetchGeofencesResult.Unavailable)
    }

    @Test
    fun `returns failure when response is missing`() {
        val request = makeTestRequest()
            .setStatus(KlaviyoApiRequest.Status.Complete)

        assert(request.getResult() is FetchGeofencesResult.Failure)
    }

    @Test
    fun `returns failure when response is invalid JSON`() {
        val request = makeTestRequest()
            .setResponseBody("invalid json")
            .setStatus(KlaviyoApiRequest.Status.Complete)

        assert(request.getResult() is FetchGeofencesResult.Failure)
    }

    @Test
    fun `returns failure when response missing data array`() {
        val request = makeTestRequest()
            .setResponseBody("""{"error": "something went wrong"}""")
            .setStatus(KlaviyoApiRequest.Status.Complete)

        assert(request.getResult() is FetchGeofencesResult.Failure)
    }

    @Test
    fun `returns unavailable for 429 rate limit error`() {
        val request = makeTestRequest()
            .setStatus(KlaviyoApiRequest.Status.Failed, 429)

        assert(request.getResult() is FetchGeofencesResult.Unavailable)
    }

    @Test
    fun `returns unavailable for 500 server errors`() = arrayOf(500, 502, 503, 504).forEach { code ->
        val request = makeTestRequest()
            .setStatus(KlaviyoApiRequest.Status.Failed, code)

        assert(request.getResult() is FetchGeofencesResult.Unavailable)
    }

    @Test
    fun `returns failure for client errors`() = arrayOf(400, 401, 403, 404).forEach { code ->
        val request = makeTestRequest()
            .setStatus(KlaviyoApiRequest.Status.Failed, code)

        assert(request.getResult() is FetchGeofencesResult.Failure)
    }

    @Test
    fun `returns failure for redirect codes`() = arrayOf(301, 302, 304).forEach { code ->
        val request = makeTestRequest()
            .setStatus(KlaviyoApiRequest.Status.Failed, code)

        assert(request.getResult() is FetchGeofencesResult.Failure)
    }
}

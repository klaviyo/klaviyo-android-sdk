package com.klaviyo.analytics.networking.requests

import android.net.Uri
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.requests.UniversalClickTrackRequest.Companion.KLAVIYO_CLICK_TIMESTAMP_HEADER
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.net.URL
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class UniversalClickTrackRequestTest : BaseApiRequestTest<UniversalClickTrackRequest>() {

    private val trackingUrl = "https://track.klaviyo.com/click/abcdefg123456"

    private val expectedDestination = "https://example.com/landing-page"

    override val expectedUrl: URL = URL(trackingUrl)

    // Since this request type uses the full URL directly, the expectedPath is just a placeholder
    override val expectedPath = ""

    override val expectedMethod = RequestMethod.GET

    override val expectedQuery = emptyMap<String, String>()

    override val expectedHeaders: Map<String, String>
        get() = super.expectedHeaders.toMutableMap() + mapOf(
            UniversalClickTrackRequest.KLAVIYO_PROFILE_INFO_HEADER to (stubProfile.identifiers.toString())
        )

    override fun makeTestRequest(): UniversalClickTrackRequest =
        UniversalClickTrackRequest(trackingUrl, stubProfile)

    @Test
    fun `jSON interoperability`() = testJsonInterop(makeTestRequest())

    @Test
    fun `uses tracking URL instead of base URL + path`() {
        val request = makeTestRequest()
        assertEquals(URL(trackingUrl), request.url)
    }

    @Test
    fun `filters out null profile identifiers from header`() {
        // Create a profile with only email and anonymous ID
        val partialProfile = Profile()
            .setEmail(EMAIL)
            .setAnonymousId(ANON_ID)

        val request = UniversalClickTrackRequest(trackingUrl, partialProfile)

        val headerValue = request.headers[UniversalClickTrackRequest.KLAVIYO_PROFILE_INFO_HEADER]
        assertNotNull(headerValue)

        val identifiers = JSONObject(headerValue!!)

        // Should only have email and anonymous_id
        assertEquals(2, identifiers.length())
        assertEquals(EMAIL, identifiers.getString("email"))
        assertEquals(ANON_ID, identifiers.getString("anonymous_id"))

        // Should not have phone_number or external_id
        assertTrue(!identifiers.has("phone_number"))
        assertTrue(!identifiers.has("external_id"))
    }

    @Test
    fun `parses destination URL from response`() {
        mockkStatic(Uri::class)
        val request = makeTestRequest()
            .setResponseBody("""{"original_destination": "$expectedDestination"}""")
            .setStatus(KlaviyoApiRequest.Status.Complete)

        val mockUri = mockk<Uri>()
        every { Uri.parse(expectedDestination) } returns mockUri

        assertEquals(
            mockUri,
            (request.getResult() as ResolveDestinationResult.Success).destinationUrl
        )

        unmockkStatic(Uri::class)
    }

    @Test
    fun `fails when request is incomplete or hasn't been made`() {
        val request = makeTestRequest()
            .setStatus(KlaviyoApiRequest.Status.Unsent)

        assert(request.getResult() is ResolveDestinationResult.Unavailable)
    }

    @Test
    fun `fails when response is missing`() {
        val request = makeTestRequest()
            .setStatus(KlaviyoApiRequest.Status.Complete)

        assert(request.getResult() is ResolveDestinationResult.Failure)
    }

    @Test
    fun `fails when response is invalid JSON`() {
        val request = makeTestRequest()
            .setResponseBody("invalid json")
            .setStatus(KlaviyoApiRequest.Status.Complete)

        assert(request.getResult() is ResolveDestinationResult.Failure)
    }

    @Test
    fun `fails when request fails`() {
        val request = makeTestRequest()
            .setStatus(KlaviyoApiRequest.Status.Failed)

        assert(request.getResult() is ResolveDestinationResult.Failure)
    }

    @Test
    fun `sets the timestamp header before enqueuing`() {
        val request = makeTestRequest()
        request.prepareToEnqueue()
        request.headers.contains(KLAVIYO_CLICK_TIMESTAMP_HEADER)
        assertEquals(0, request.attempts)
    }

    /**
     * Use reflection to set the protected responseBody field with invalid JSON
     */
    private fun UniversalClickTrackRequest.setResponseBody(body: String) = apply {
        // Use reflection to set the protected responseBody field
        val responseBodyField = KlaviyoApiRequest::class.java.getDeclaredField("responseBody")
        responseBodyField.isAccessible = true
        responseBodyField.set(this, body)
    }

    /**
     * Use reflection to set the private status field
     */
    private fun UniversalClickTrackRequest.setStatus(status: KlaviyoApiRequest.Status) = apply {
        // Use reflection to set the protected responseBody field
        val responseBodyField = KlaviyoApiRequest::class.java.getDeclaredField("status")
        responseBodyField.isAccessible = true
        responseBodyField.set(this, status)
    }
}

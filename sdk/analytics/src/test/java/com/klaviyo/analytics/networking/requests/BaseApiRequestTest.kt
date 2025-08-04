package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Profile
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import io.mockk.every
import java.net.URL
import org.junit.Assert
import org.junit.Before
import org.junit.Test

internal abstract class BaseApiRequestTest<T> : BaseTest() where T : KlaviyoApiRequest {

    abstract val expectedPath: String

    open val expectedUrl: URL get() = URL("${mockConfig.baseUrl}/$expectedPath?company_id=$API_KEY")

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

    abstract fun makeTestRequest(): T

    @Before
    override fun setup() {
        super.setup()
        mockDeviceProperties()
    }

    @Test
    fun `Uses expected URL`() {
        Assert.assertEquals(expectedUrl.toString(), makeTestRequest().url.toString())
    }

    @Test
    fun `Uses expected URL after encoding and decoding even if base url has changed`() {
        val requestJson = makeTestRequest().toJson()
        val expectedUrl = expectedUrl
        every { mockConfig.baseUrl } returns "https://test-two.fake-klaviyo.com"
        val revivedRequest = KlaviyoApiRequestDecoder.fromJson(requestJson)
        Assert.assertEquals(expectedUrl.toString(), revivedRequest.url.toString())
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

    /**
     * Tests that the request can be serialized to JSON and then revived to the same type.
     * Because the type must be reified, this function must be called from the subclass.
     */
    inline fun <reified T> testJsonInterop(request: T) where T : KlaviyoApiRequest {
        val requestJson = request.toJson()
        val revivedRequest = KlaviyoApiRequestDecoder.fromJson(requestJson)
        assert(revivedRequest is T)
        compareJson(requestJson, revivedRequest.toJson())
    }
}

package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Profile
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
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

    abstract fun makeTestRequest(): T

    @Before
    override fun setup() {
        super.setup()
        mockDeviceProperties()
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

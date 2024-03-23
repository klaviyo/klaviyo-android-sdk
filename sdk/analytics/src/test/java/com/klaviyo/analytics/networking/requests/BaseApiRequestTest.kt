package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.DevicePropertiesTest
import com.klaviyo.analytics.model.Profile
import com.klaviyo.fixtures.BaseTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test

internal abstract class BaseApiRequestTest<T> : BaseTest() where T : KlaviyoApiRequest {

    abstract val expectedUrl: String

    open val expectedMethod = RequestMethod.POST

    open val expectedHeaders = mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "Revision" to "2023-07-15",
        "User-Agent" to "Mock User Agent",
        "X-Klaviyo-Mobile" to "1",
        "X-Klaviyo-Retry-Attempt" to "0/4"
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

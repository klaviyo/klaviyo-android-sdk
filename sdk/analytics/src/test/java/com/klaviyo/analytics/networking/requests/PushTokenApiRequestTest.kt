package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Test

internal class PushTokenApiRequestTest : BaseTest() {
    private val expectedUrlPath = "api/identify"
    private val expectedMethod = RequestMethod.POST
    private var profile = Profile().setAnonymousId(ANON_ID)

    private val expectedHeaders = mapOf(
        "Accept" to "text/html",
        "Content-Type" to "application/x-www-form-urlencoded"
    )

    @Test
    fun `Uses the correct endpoint`() {
        assertEquals(expectedUrlPath, PushTokenApiRequest(PUSH_TOKEN, profile).urlPath)
    }

    @Test
    fun `Uses the correct method`() {
        assertEquals(expectedMethod, PushTokenApiRequest(PUSH_TOKEN, profile).method)
    }

    @Test
    fun `Sets proper headers`() {
        assertEquals(expectedHeaders, PushTokenApiRequest(PUSH_TOKEN, profile).headers)
    }

    @Test
    fun `Does not set a query`() {
        assert(PushTokenApiRequest(PUSH_TOKEN, profile).query.isEmpty())
    }

    @Test
    fun `Body includes only API key, profile identifiers, and push token`() {
        profile
            .setExternalId(EXTERNAL_ID)
            .setEmail(EMAIL)
            .setPhoneNumber(PHONE)
            .setProperty(ProfileKey.FIRST_NAME, "Kermit")
            .setProperty("type", "muppet")

        val request = PushTokenApiRequest(PUSH_TOKEN, profile)
        val props = request.body?.optJSONObject("properties")

        assertEquals(API_KEY, request.body?.optString("token"))
        assertEquals(EXTERNAL_ID, props?.optString("\$external_id"))
        assertEquals(EMAIL, props?.optString("\$email"))
        assertEquals(PHONE, props?.optString("\$phone_number"))
        assertEquals(ANON_ID, props?.optString("\$anonymous"))
        assertEquals(PUSH_TOKEN, props?.optJSONObject("\$append")?.optString("\$android_tokens"))
        assertEquals(5, props?.length()) // no other fields!

        // Already confirmed the contents, just confirm that the body uses this odd data=json format, url encoded
        assertEquals(request.formatBody(), "data=" + URLEncoder.encode("${request.body}", "utf-8"))
    }

    @Test
    fun `Parses response string for errors`() {
        // V2 API returns a 200 status code with "0" or "1" in the payload where
        // "0" = failures that would typically be a 400 status code
        // "1" = success
        val successStream = ByteArrayInputStream("1".toByteArray())
        val errorStream = ByteArrayInputStream("0".toByteArray())
        val expectedUrl = URL(configMock.baseUrl + "/$expectedUrlPath")
        val connectionMock = spyk(expectedUrl.openConnection()) as HttpURLConnection

        mockkObject(HttpUtil)
        every { HttpUtil.openConnection(any()) } returns connectionMock
        every { HttpUtil.writeToConnection(any(), any()) } returns Unit
        every { connectionMock.connect() } returns Unit
        every { networkMonitorMock.isNetworkConnected() } returns true
        every { configMock.networkTimeout } returns 1

        val request = PushTokenApiRequest(PUSH_TOKEN, profile)

        // Set up a 200 response with different bodies
        every { connectionMock.responseCode } returns 200
        every { connectionMock.inputStream } returns successStream
        assertEquals(KlaviyoApiRequest.Status.Complete, request.send())

        every { connectionMock.inputStream } returns errorStream
        assertEquals(KlaviyoApiRequest.Status.Failed, request.send())
    }
}

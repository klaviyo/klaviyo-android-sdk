package com.klaviyo.coresdk.networking.requests

import com.klaviyo.coresdk.BaseTest
import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.KlaviyoEventType
import com.klaviyo.coresdk.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

internal class TrackApiRequestTest : BaseTest() {

    private val expectedUrlPath = "client/events"

    private val expectedQueryData = mapOf("company_id" to API_KEY)

    private val expectedHeaders = mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "Revision" to "2022-10-17"
    )

    private val stubEvent = KlaviyoEventType.CUSTOM("Test Event")

    private val stubProfile = Profile()
        .setAnonymousId(ANON_ID)
        .setEmail(EMAIL)
        .setPhoneNumber(PHONE)

    @Test
    fun `Build Track request with no properties successfully`() {
        val expectedJsonString = "{\"data\":{\"type\":\"event\",\"attributes\":{\"metric\":{\"name\":\"$stubEvent\"},\"profile\":{\"\$email\":\"$EMAIL\",\"\$anonymous\":\"$ANON_ID\",\"\$phone_number\":\"$PHONE\"},\"time\":\"$ISO_TIME\"}}}"
        val request = TrackApiRequest(stubEvent, stubProfile)

        assertEquals(expectedUrlPath, request.urlPath)
        assertEquals(RequestMethod.POST, request.method)
        assertEquals(expectedQueryData, request.query)
        assertEquals(expectedHeaders, request.headers)
        assertEquals(expectedJsonString, request.body)
    }

    @Test
    fun `Build Track request successfully`() {
        val stubProperties = Event().setProperty("custom_value", "200")
        val expectedJsonString = "{\"data\":{\"type\":\"event\",\"attributes\":{\"time\":\"$ISO_TIME\",\"metric\":{\"name\":\"$stubEvent\"},\"properties\":{\"custom_value\":\"200\"},\"profile\":{\"\$email\":\"$EMAIL\",\"\$anonymous\":\"$ANON_ID\",\"\$phone_number\":\"$PHONE\"}}}}"
        val request = TrackApiRequest(stubEvent, stubProfile, stubProperties)

        assertEquals(expectedUrlPath, request.urlPath)
        assertEquals(RequestMethod.POST, request.method)
        assertEquals(expectedQueryData, request.query)
        assertEquals(expectedHeaders, request.headers)
        assertEquals(expectedJsonString, request.body)
    }

    @Test
    fun `Profile data is snapshotted to prevent mutation`() {
        val stubProperties = Event().setProperty("custom_value", "200")
        val stubProfile = Profile()
            .setAnonymousId(ANON_ID)
            .setEmail(EMAIL)
            .setPhoneNumber(PHONE)

        val expectedJsonString = "{\"data\":{\"type\":\"event\",\"attributes\":{\"time\":\"$ISO_TIME\",\"metric\":{\"name\":\"$stubEvent\"},\"properties\":{\"custom_value\":\"200\"},\"profile\":{\"\$email\":\"$EMAIL\",\"\$anonymous\":\"$ANON_ID\",\"\$phone_number\":\"$PHONE\"}}}}"

        val request = TrackApiRequest(stubEvent, stubProfile, stubProperties)

        // If I mutate profile or properties after creating, it shouldn't affect the request
        stubProfile.setIdentifier("ext_id")
        stubProperties.setProperty("custom_value", "100")

        assertEquals(expectedJsonString, request.body)
    }
}

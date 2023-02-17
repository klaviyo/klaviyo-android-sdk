package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventType
import com.klaviyo.analytics.model.Profile
import com.klaviyo.core_shared_tests.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EventApiRequestTest : BaseTest() {

    private val expectedUrlPath = "client/events"

    private val expectedQueryData = mapOf("company_id" to API_KEY)

    private val expectedHeaders = mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "Revision" to "2023-01-24"
    )

    private val stubEvent: Event = Event(EventType.CUSTOM("Test Event"))

    private val stubProfile = Profile()
        .setAnonymousId(ANON_ID)
        .setEmail(EMAIL)
        .setPhoneNumber(PHONE)

    @Test
    fun `Uses correct endpoint`() {
        assertEquals(expectedUrlPath, EventApiRequest(stubEvent, stubProfile).urlPath)
    }

    @Test
    fun `Uses correct method`() {
        assertEquals(RequestMethod.POST, EventApiRequest(stubEvent, stubProfile).method)
    }

    @Test
    fun `Uses correct headers`() {
        assertEquals(expectedHeaders, EventApiRequest(stubEvent, stubProfile).headers)
    }

    @Test
    fun `Uses API Key in query`() {
        assertEquals(expectedQueryData, EventApiRequest(stubEvent, stubProfile).query)
    }

    @Test
    fun `Builds body request without properties`() {
        val expectedJsonString = "{\"data\":{\"type\":\"event\",\"attributes\":{\"metric\":{\"name\":\"${stubEvent.type}\"},\"profile\":{\"\$email\":\"$EMAIL\",\"\$anonymous\":\"$ANON_ID\",\"\$phone_number\":\"$PHONE\"},\"time\":\"$ISO_TIME\"}}}"
        val request = EventApiRequest(stubEvent, stubProfile)

        assertEquals(expectedJsonString, request.body.toString())
    }

    @Test
    fun `Builds request with properties`() {
        stubEvent.setProperty("custom_value", "200")
        val expectedJsonString = "{\"data\":{\"type\":\"event\",\"attributes\":{\"time\":\"$ISO_TIME\",\"metric\":{\"name\":\"${stubEvent.type}\"},\"properties\":{\"custom_value\":\"200\"},\"profile\":{\"\$email\":\"$EMAIL\",\"\$anonymous\":\"$ANON_ID\",\"\$phone_number\":\"$PHONE\"}}}}"
        val request = EventApiRequest(stubEvent, stubProfile)

        assertEquals(expectedJsonString, request.body.toString())
    }

    @Test
    fun `Request is unaffected by changes to profile or event after the fact`() {
        stubEvent.setProperty("custom_value", "200")

        val expectedJsonString = "{\"data\":{\"type\":\"event\",\"attributes\":{\"time\":\"$ISO_TIME\",\"metric\":{\"name\":\"${stubEvent.type}\"},\"properties\":{\"custom_value\":\"200\"},\"profile\":{\"\$email\":\"$EMAIL\",\"\$anonymous\":\"$ANON_ID\",\"\$phone_number\":\"$PHONE\"}}}}"

        val request = EventApiRequest(stubEvent, stubProfile)

        // If I mutate profile or properties after creating, it shouldn't affect the request
        stubProfile.setExternalId("ext_id")
        stubEvent.setProperty("custom_value", "100")

        assertEquals(expectedJsonString, request.body.toString())
    }
}

package com.klaviyo.analytics.networking.requests

import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import com.klaviyo.fixtures.unmockDeviceProperties
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class ApiLaneTest : BaseTest() {

    @Before
    override fun setup() {
        super.setup()
        mockDeviceProperties()
    }

    @After
    override fun cleanup() {
        super.cleanup()
        unmockDeviceProperties()
    }

    @Test
    fun `identity endpoints map to IDENTITY lane`() {
        listOf(
            "client/profiles",
            "client/push-tokens",
            "client/push-token-unregister",
            "client/subscriptions"
        ).forEach { path ->
            assertEquals(ApiLane.IDENTITY, ApiLane.fromUrlPath(path))
        }
    }

    @Test
    fun `events endpoint maps to EVENTS lane`() {
        assertEquals(ApiLane.EVENTS, ApiLane.fromUrlPath("client/events"))
    }

    @Test
    fun `onsite track analytics maps to ENGAGEMENT lane`() {
        assertEquals(ApiLane.ENGAGEMENT, ApiLane.fromUrlPath("onsite/track-analytics"))
    }

    @Test
    fun `click-tracking request with empty path maps to ENGAGEMENT lane`() {
        // UniversalClickTrackRequest carries an empty urlPath and uses baseUrl for the URL
        assertEquals(ApiLane.ENGAGEMENT, ApiLane.fromUrlPath(""))
    }

    @Test
    fun `unknown endpoint falls back to ENGAGEMENT lane`() {
        assertEquals(ApiLane.ENGAGEMENT, ApiLane.fromUrlPath("client/geofences"))
        assertEquals(ApiLane.ENGAGEMENT, ApiLane.fromUrlPath("some/future/endpoint"))
    }

    @Test
    fun `prefixes match from the start of the path`() {
        // A path that merely contains a known prefix must not be classified by it
        assertEquals(ApiLane.ENGAGEMENT, ApiLane.fromUrlPath("x/client/events"))
    }

    @Test
    fun `fromRequest classifies by the request endpoint`() {
        val identity = KlaviyoApiRequest("client/profiles", RequestMethod.POST)
        val event = KlaviyoApiRequest("client/events", RequestMethod.POST)
        val engagement = KlaviyoApiRequest("onsite/track-analytics", RequestMethod.POST)

        assertEquals(ApiLane.IDENTITY, ApiLane.fromRequest(identity))
        assertEquals(ApiLane.EVENTS, ApiLane.fromRequest(event))
        assertEquals(ApiLane.ENGAGEMENT, ApiLane.fromRequest(engagement))
    }
}

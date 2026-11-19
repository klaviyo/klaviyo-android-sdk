package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Profile
import com.klaviyo.core.config.MissingAPIKey
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import com.klaviyo.fixtures.unmockDeviceProperties
import io.mockk.every
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

internal class KlaviyoApiRequestDecoderTest : BaseTest() {
    @Before
    override fun setup() {
        super.setup()
        mockDeviceProperties()
    }

    @After
    override fun cleanup() {
        unmockDeviceProperties()
        super.cleanup()
    }

    private fun requestFactories(): List<() -> KlaviyoApiRequest> = listOf(
        { ProfileApiRequest() },
        { EventApiRequest() },
        { PushTokenApiRequest() },
        { UnregisterPushTokenApiRequest() },
        { SubscriptionApiRequest() },
        { AggregateEventApiRequest() },
        { FetchGeofencesRequest() },
        { UniversalClickTrackRequest() },
        { KlaviyoApiRequest("other", RequestMethod.GET) }
    )

    @Test
    fun `restores each request type and full saved query without an API key`() {
        val savedRequests = requestFactories().map { makeRequest ->
            makeRequest().apply {
                query = mapOf("company_id" to "old-account", "extra" to "value")
            }.let { it.javaClass to it.toJson() }
        }
        every { mockConfig.apiKey } throws MissingAPIKey()

        savedRequests.forEach { (type, json) ->
            val restored = KlaviyoApiRequestDecoder.fromJson(json)
            assertEquals(type, restored.javaClass)
            assertEquals(mapOf("company_id" to "old-account", "extra" to "value"), restored.query)
        }
    }

    @Test
    fun `restores an empty saved query without an API key`() {
        val savedRequests = requestFactories().map { makeRequest ->
            makeRequest().apply { query = emptyMap() }.toJson()
        }
        every { mockConfig.apiKey } throws MissingAPIKey()

        savedRequests.forEach { json ->
            assertEquals(emptyMap<String, String>(), KlaviyoApiRequestDecoder.fromJson(json).query)
        }
    }

    @Test
    fun `restores saved accounts after the configured account changes`() {
        val savedRequests = listOf("account-one", "account-two").map { account ->
            every { mockConfig.apiKey } returns account
            EventApiRequest().toJson()
        }
        every { mockConfig.apiKey } returns "current-account"

        val restoredQueries = savedRequests.map { KlaviyoApiRequestDecoder.fromJson(it).query }
        assertEquals(
            listOf(mapOf("company_id" to "account-one"), mapOf("company_id" to "account-two")),
            restoredQueries
        )
        assertEquals(mapOf("company_id" to "current-account"), EventApiRequest().query)
    }

    @Test
    fun `restores geofence pagination with the saved account`() {
        val json = FetchGeofencesRequest().toJson()
        every { mockConfig.apiKey } throws MissingAPIKey()

        val restored = KlaviyoApiRequestDecoder.fromJson(json)
        assertEquals(FetchGeofencesRequest::class.java, restored.javaClass)
        assertEquals(
            mapOf("company_id" to API_KEY, "page[size]" to "30"),
            restored.query
        )
    }

    @Test
    fun `restores the old account on unregister after switching accounts`() {
        every { mockConfig.apiKey } returns "new-account"
        val request = UnregisterPushTokenApiRequest(
            "old-account",
            PUSH_TOKEN,
            Profile(email = EMAIL)
        )
        val restored = KlaviyoApiRequestDecoder.fromJson(request.toJson())

        assertEquals(UnregisterPushTokenApiRequest::class.java, restored.javaClass)
        assertEquals(mapOf("company_id" to "old-account"), restored.query)
        assertEquals(request, restored)
    }

    @Test
    fun `new keyed requests still require a configured API key`() {
        every { mockConfig.apiKey } throws MissingAPIKey()
        requestFactories().take(7).forEach { makeRequest ->
            assertThrows(MissingAPIKey::class.java) { makeRequest() }
        }
        assertThrows(MissingAPIKey::class.java) {
            UnregisterPushTokenApiRequest("old-account", PUSH_TOKEN, Profile(email = EMAIL))
        }
    }
}

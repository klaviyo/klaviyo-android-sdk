package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.Subscription
import io.mockk.verify
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Test

internal class SubscriptionApiRequestTest : BaseApiRequestTest<SubscriptionApiRequest>() {

    override val expectedPath = "client/subscriptions"

    private val listId = "abc123"

    private val fullChannels = Subscription.Channels(
        email = setOf(
            Subscription.Channels.Email.MARKETING,
            Subscription.Channels.Email.OPEN_TRACKING
        ),
        sms = setOf(
            Subscription.Channels.Messaging.MARKETING,
            Subscription.Channels.Messaging.TRANSACTIONAL
        ),
        whatsapp = setOf(
            Subscription.Channels.Messaging.MARKETING,
            Subscription.Channels.Messaging.TRANSACTIONAL
        )
    )

    override fun makeTestRequest(): SubscriptionApiRequest =
        SubscriptionApiRequest(Subscription(listId, fullChannels), stubProfile)

    @Test
    fun `JSON interoperability`() = testJsonInterop(makeTestRequest())

    @Test
    fun `Formats full channel body correctly`() {
        val expectJson = """{
            "data": {
                "type": "subscription",
                "attributes": {
                    "profile": {
                        "data": {
                            "type": "profile",
                            "attributes": {
                                "email": "$EMAIL",
                                "phone_number": "$PHONE",
                                "external_id": "$EXTERNAL_ID",
                                "anonymous_id": "$ANON_ID",
                                "subscriptions": {
                                    "email": {
                                        "marketing": { "consent": "SUBSCRIBED" },
                                        "open_tracking": { "consent": "SUBSCRIBED" }
                                    },
                                    "sms": {
                                        "marketing": { "consent": "SUBSCRIBED" },
                                        "transactional": { "consent": "SUBSCRIBED" }
                                    },
                                    "whatsapp": {
                                        "marketing": { "consent": "SUBSCRIBED" },
                                        "transactional": { "consent": "SUBSCRIBED" }
                                    }
                                }
                            }
                        }
                    }
                },
                "relationships": {
                    "list": {
                        "data": { "type": "list", "id": "$listId" }
                    }
                }
            }
        }"""

        val request = SubscriptionApiRequest(Subscription(listId, fullChannels), stubProfile)

        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }

    @Test
    fun `Includes custom source when provided`() {
        val request = SubscriptionApiRequest(
            Subscription(listId, fullChannels, customSource = "signup form"),
            stubProfile
        )

        val attributes = JSONObject(request.requestBody!!)
            .getJSONObject("data")
            .getJSONObject("attributes")

        assert(attributes.getString("custom_source") == "signup form")
    }

    @Test
    fun `allAvailableMarketing omits subscriptions object`() {
        val request = SubscriptionApiRequest(
            Subscription.allAvailableMarketing(listId),
            stubProfile
        )

        val profileAttributes = JSONObject(request.requestBody!!)
            .getJSONObject("data")
            .getJSONObject("attributes")
            .getJSONObject("profile")
            .getJSONObject("data")
            .getJSONObject("attributes")

        // subscriptions is omitted so the server applies its default marketing consent...
        assert(!profileAttributes.has("subscriptions"))
        // ...but the profile identifiers are still sent so the server can key channels on them
        assert(profileAttributes.getString("email") == EMAIL)
        assert(profileAttributes.getString("phone_number") == PHONE)
        assert(profileAttributes.getString("external_id") == EXTERNAL_ID)
        assert(profileAttributes.getString("anonymous_id") == ANON_ID)
    }

    @Test
    fun `Serializes email-only channel`() {
        val expectJson = """{
            "email": {
                "marketing": { "consent": "SUBSCRIBED" }
            }
        }"""
        val channels = Subscription.Channels(
            email = setOf(Subscription.Channels.Email.MARKETING)
        )

        val request = SubscriptionApiRequest(Subscription(listId, channels), stubProfile)

        compareJson(JSONObject(expectJson), subscriptionsOf(request))
    }

    @Test
    fun `Serializes sms-only channel`() {
        val expectJson = """{
            "sms": {
                "transactional": { "consent": "SUBSCRIBED" }
            }
        }"""
        val channels = Subscription.Channels(
            sms = setOf(Subscription.Channels.Messaging.TRANSACTIONAL)
        )

        val request = SubscriptionApiRequest(Subscription(listId, channels), stubProfile)

        compareJson(JSONObject(expectJson), subscriptionsOf(request))
    }

    @Test
    fun `Drops request when email consent requested but profile has no email`() {
        val channels = Subscription.Channels(
            email = setOf(Subscription.Channels.Email.MARKETING)
        )

        val request = SubscriptionApiRequest.from(
            Subscription(listId, channels),
            Profile().setPhoneNumber(PHONE)
        )

        assertDropped(request)
    }

    @Test
    fun `Drops request when messaging consent requested but profile has no phone`() {
        val channels = Subscription.Channels(
            sms = setOf(Subscription.Channels.Messaging.MARKETING)
        )

        val request = SubscriptionApiRequest.from(
            Subscription(listId, channels),
            Profile().setEmail(EMAIL)
        )

        assertDropped(request)
    }

    @Test
    fun `Drops request when no consent sub-types are selected`() {
        val request = SubscriptionApiRequest.from(
            Subscription(listId, Subscription.Channels()),
            stubProfile
        )

        assertDropped(request)
    }

    @Test
    fun `Drops allAvailableMarketing when profile has no identifiers`() {
        val request = SubscriptionApiRequest.from(
            Subscription.allAvailableMarketing(listId),
            Profile()
        )

        assertDropped(request)
    }

    @Test
    fun `Builds request when validation passes`() {
        val request = SubscriptionApiRequest.from(
            Subscription(listId, fullChannels),
            stubProfile
        )

        assert(request != null)
        verify(exactly = 0) { spyLog.warning(any<String>(), any()) }
    }

    /**
     * Asserts a subscription was dropped in validation: no request built, and a warning logged.
     */
    private fun assertDropped(request: SubscriptionApiRequest?) {
        assertNull(request)
        verify { spyLog.warning(any<String>(), null) }
    }

    /**
     * Extracts the `subscriptions` object from a built request body for focused assertions.
     */
    private fun subscriptionsOf(request: SubscriptionApiRequest): JSONObject =
        JSONObject(request.requestBody!!)
            .getJSONObject("data")
            .getJSONObject("attributes")
            .getJSONObject("profile")
            .getJSONObject("data")
            .getJSONObject("attributes")
            .getJSONObject("subscriptions")
}

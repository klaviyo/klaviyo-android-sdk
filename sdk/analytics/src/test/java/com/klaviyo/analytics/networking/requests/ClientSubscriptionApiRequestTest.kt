package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Subscription
import com.klaviyo.analytics.model.SubscriptionChannel
import org.json.JSONObject
import org.junit.Test

internal class ClientSubscriptionApiRequestTest : BaseApiRequestTest<ClientSubscriptionApiRequest>() {

    override val expectedPath = "client/subscriptions"

    private val stubListId = "test-list-id"
    private val stubSubscription = Subscription(stubListId)

    override fun makeTestRequest(): ClientSubscriptionApiRequest =
        ClientSubscriptionApiRequest(stubProfile, stubSubscription)

    @Test
    fun `JSON interoperability`() = testJsonInterop(makeTestRequest())

    @Test
    fun `Formats body correctly with no channels specified`() {
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
                                "anonymous_id": "$ANON_ID"
                            }
                        }
                    },
                    "list_id": "$stubListId",
                    "custom_source": "Android SDK"
                }
            }
        }"""

        val request = ClientSubscriptionApiRequest(stubProfile, Subscription(stubListId))

        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }

    @Test
    fun `Formats body correctly with email channel`() {
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
                                "anonymous_id": "$ANON_ID"
                            }
                        }
                    },
                    "list_id": "$stubListId",
                    "custom_source": "Android SDK",
                    "subscriptions": {
                        "email": {
                            "marketing": {
                                "consent": "marketing"
                            }
                        }
                    }
                }
            }
        }"""

        val subscription = Subscription(stubListId, setOf(SubscriptionChannel.EMAIL))
        val request = ClientSubscriptionApiRequest(stubProfile, subscription)

        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }

    @Test
    fun `Formats body correctly with SMS channel`() {
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
                                "anonymous_id": "$ANON_ID"
                            }
                        }
                    },
                    "list_id": "$stubListId",
                    "custom_source": "Android SDK",
                    "subscriptions": {
                        "sms": {
                            "marketing": {
                                "consent": "marketing"
                            }
                        }
                    }
                }
            }
        }"""

        val subscription = Subscription(stubListId, setOf(SubscriptionChannel.SMS))
        val request = ClientSubscriptionApiRequest(stubProfile, subscription)

        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }

    @Test
    fun `Formats body correctly with both email and SMS channels`() {
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
                                "anonymous_id": "$ANON_ID"
                            }
                        }
                    },
                    "list_id": "$stubListId",
                    "custom_source": "Android SDK",
                    "subscriptions": {
                        "email": {
                            "marketing": {
                                "consent": "marketing"
                            }
                        },
                        "sms": {
                            "marketing": {
                                "consent": "marketing"
                            }
                        }
                    }
                }
            }
        }"""

        val subscription = Subscription(
            stubListId,
            setOf(SubscriptionChannel.EMAIL, SubscriptionChannel.SMS)
        )
        val request = ClientSubscriptionApiRequest(stubProfile, subscription)

        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }

    @Test
    fun `Subscription builder creates correct subscription`() {
        val subscription = Subscription.builder(stubListId)
            .subscribeToEmail()
            .subscribeToSms()
            .build()

        assert(subscription.listId == stubListId)
        assert(subscription.channels.contains(SubscriptionChannel.EMAIL))
        assert(subscription.channels.contains(SubscriptionChannel.SMS))
    }
}

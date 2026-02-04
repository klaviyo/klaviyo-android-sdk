package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.Subscription
import com.klaviyo.analytics.model.SubscriptionChannel
import com.klaviyo.core.Registry

/**
 * Defines the content of an API request to create a subscription for a [Profile]
 *
 * Using the Client Subscription API (POST /client/subscriptions)
 *
 * @constructor
 */
internal class ClientSubscriptionApiRequest(
    queuedTime: Long? = null,
    uuid: String? = null
) : KlaviyoApiRequest(PATH, RequestMethod.POST, queuedTime, uuid) {

    private companion object {
        const val PATH = "client/subscriptions"
        const val SUBSCRIPTION = "subscription"
        const val LIST_ID = "list_id"
        const val CUSTOM_SOURCE = "custom_source"
        const val CUSTOM_SOURCE_VALUE = "Android SDK"

        // Subscription channel constants
        const val EMAIL_CHANNEL = "email"
        const val SMS_CHANNEL = "sms"
        const val MARKETING = "marketing"
        const val CONSENT = "consent"
    }

    override var type: String = "Create Subscription"

    override var query: Map<String, String> = mapOf(
        COMPANY_ID to Registry.config.apiKey
    )

    override val successCodes: IntRange get() = HTTP_ACCEPTED..HTTP_ACCEPTED

    constructor(profile: Profile, subscription: Subscription) : this() {
        body = jsonMapOf(
            DATA to mapOf(
                TYPE to SUBSCRIPTION,
                ATTRIBUTES to filteredMapOf(
                    PROFILE to mapOf(*ProfileApiRequest.formatBody(profile)),
                    LIST_ID to subscription.listId,
                    CUSTOM_SOURCE to CUSTOM_SOURCE_VALUE,
                    *buildSubscriptionChannels(subscription.channels)
                )
            )
        )
    }

    /**
     * Builds the subscription channels array for the request body
     */
    private fun buildSubscriptionChannels(
        channels: Set<SubscriptionChannel>
    ): Array<Pair<String, Any>> {
        if (channels.isEmpty()) {
            return emptyArray()
        }

        val subscriptionsMap = mutableMapOf<String, Any>()

        if (channels.contains(SubscriptionChannel.EMAIL)) {
            subscriptionsMap[EMAIL_CHANNEL] = mapOf(MARKETING to mapOf(CONSENT to MARKETING))
        }

        if (channels.contains(SubscriptionChannel.SMS)) {
            subscriptionsMap[SMS_CHANNEL] = mapOf(MARKETING to mapOf(CONSENT to MARKETING))
        }

        return if (subscriptionsMap.isNotEmpty()) {
            arrayOf("subscriptions" to subscriptionsMap)
        } else {
            emptyArray()
        }
    }
}

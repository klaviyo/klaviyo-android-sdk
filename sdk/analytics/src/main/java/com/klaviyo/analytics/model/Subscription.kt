package com.klaviyo.analytics.model

/**
 * Represents the type of subscription consent to request
 */
enum class SubscriptionChannel {
    EMAIL,
    SMS
}

/**
 * Represents a subscription request to be sent to the Create Client Subscription API
 *
 * @property listId The ID of the Klaviyo list to subscribe the profile to
 * @property channels The subscription channels to request consent for (email, sms, or both).
 *                   If empty, the API defaults to MARKETING for available channels based on
 *                   the profile identifiers provided (email for email channel, phone for SMS).
 */
class Subscription(
    val listId: String,
    val channels: Set<SubscriptionChannel> = emptySet()
) {
    /**
     * Builder class for creating Subscription objects, provided for Java interoperability
     */
    class Builder(private val listId: String) {
        private val channels = mutableSetOf<SubscriptionChannel>()

        /**
         * Subscribe to email marketing channel
         */
        fun subscribeToEmail(): Builder = apply {
            channels.add(SubscriptionChannel.EMAIL)
        }

        /**
         * Subscribe to SMS marketing channel
         */
        fun subscribeToSms(): Builder = apply {
            channels.add(SubscriptionChannel.SMS)
        }

        /**
         * Build the Subscription object
         */
        fun build(): Subscription = Subscription(listId, channels)
    }

    companion object {
        /**
         * Create a Subscription builder for the given list ID
         */
        @JvmStatic
        fun builder(listId: String): Builder = Builder(listId)
    }
}

package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.model.Subscription
import com.klaviyo.core.Registry

/**
 * Defines the content of an API request to subscribe a [Profile] to a Klaviyo list
 *
 * Using V3 API
 *
 * @constructor
 */
internal class SubscriptionApiRequest(
    queuedTime: Long? = null,
    uuid: String? = null
) : KlaviyoApiRequest(PATH, RequestMethod.POST, queuedTime, uuid) {

    companion object {
        private const val PATH = "client/subscriptions"

        private const val SUBSCRIPTION = "subscription"
        private const val CUSTOM_SOURCE = "custom_source"
        private const val RELATIONSHIPS = "relationships"
        private const val SUBSCRIPTIONS = "subscriptions"
        private const val LIST = "list"
        private const val ID = "id"

        private const val CHANNEL_EMAIL = "email"
        private const val CHANNEL_SMS = "sms"
        private const val CHANNEL_WHATSAPP = "whatsapp"

        private const val MARKETING = "marketing"
        private const val OPEN_TRACKING = "open_tracking"
        private const val TRANSACTIONAL = "transactional"

        private const val CONSENT = "consent"
        private const val SUBSCRIBED = "SUBSCRIBED"

        private val subscribedConsent = mapOf(CONSENT to SUBSCRIBED)

        /**
         * Build a validated subscription request, or return `null` (logging a warning) when the
         * requested consent cannot be satisfied by [profile]. Validation runs at enqueue time so a
         * buffered subscription is checked against the latest available profile identifiers.
         *
         * @return The request to enqueue, or `null` if it should be dropped
         */
        fun from(subscription: Subscription, profile: Profile): SubscriptionApiRequest? {
            val channels = subscription.channels

            if (channels == null) {
                // allAvailableMarketing defers to the server default, but needs an identifier to act on
                if (profile.email.isNullOrEmpty() && profile.phoneNumber.isNullOrEmpty()) {
                    Registry.log.warning(
                        "Dropping subscription: allAvailableMarketing requires an email or phone identifier"
                    )
                    return null
                }
            } else {
                if (channels.needsEmail && profile.email.isNullOrEmpty()) {
                    Registry.log.warning(
                        "Dropping subscription: email consent requested but profile has no email"
                    )
                    return null
                }
                if (channels.needsPhone && profile.phoneNumber.isNullOrEmpty()) {
                    Registry.log.warning(
                        "Dropping subscription: SMS/WhatsApp consent requested but profile has no phone number"
                    )
                    return null
                }
                // No channel named a consent sub-type: nothing to send
                if (!channels.needsEmail && !channels.needsPhone) {
                    Registry.log.warning(
                        "Dropping subscription: no consent sub-types were selected"
                    )
                    return null
                }
            }

            return SubscriptionApiRequest(subscription, profile)
        }

        fun formatBody(subscription: Subscription, profile: Profile): Array<Pair<String, Any>> {
            val profileData = mapOf(
                DATA to mapOf(
                    TYPE to PROFILE,
                    ATTRIBUTES to filteredMapOf(
                        ProfileKey.EMAIL.name to (profile.email ?: ""),
                        ProfileKey.PHONE_NUMBER.name to (profile.phoneNumber ?: ""),
                        ProfileKey.EXTERNAL_ID.name to (profile.externalId ?: ""),
                        ProfileKey.ANONYMOUS_ID.name to (profile.anonymousId ?: ""),
                        // Omitted entirely for allAvailableMarketing so the server applies defaults
                        SUBSCRIPTIONS to (subscription.channels?.toApiConsent() ?: emptyMap<String, Any>())
                    )
                )
            )

            return arrayOf(
                DATA to mapOf(
                    TYPE to SUBSCRIPTION,
                    ATTRIBUTES to filteredMapOf(
                        CUSTOM_SOURCE to (subscription.customSource ?: ""),
                        PROFILE to profileData
                    ),
                    RELATIONSHIPS to mapOf(
                        LIST to mapOf(
                            DATA to mapOf(
                                TYPE to LIST,
                                ID to subscription.listId
                            )
                        )
                    )
                )
            )
        }

        /** Whether the requested channels need an email identifier on the profile. */
        private val Subscription.Channels.needsEmail: Boolean
            get() = !email.isNullOrEmpty()

        /** Whether the requested channels need a phone number identifier on the profile. */
        private val Subscription.Channels.needsPhone: Boolean
            get() = !sms.isNullOrEmpty() || !whatsapp.isNullOrEmpty()

        /**
         * Maps the requested channels to the API's `subscriptions` object, or `null` when no consent
         * sub-types were selected (nothing to send).
         */
        private fun Subscription.Channels.toApiConsent(): Map<String, Any>? = filteredMapOf(
            CHANNEL_EMAIL to emailConsent(email),
            CHANNEL_SMS to messagingConsent(sms),
            CHANNEL_WHATSAPP to messagingConsent(whatsapp)
        ).ifEmpty { null }

        private fun emailConsent(set: Set<Subscription.Channels.Email>?): Map<String, Any> =
            set?.let {
                filteredMapOf(
                    MARKETING to consentIf(it.contains(Subscription.Channels.Email.MARKETING)),
                    OPEN_TRACKING to consentIf(
                        it.contains(Subscription.Channels.Email.OPEN_TRACKING)
                    )
                )
            } ?: emptyMap()

        private fun messagingConsent(set: Set<Subscription.Channels.Messaging>?): Map<String, Any> =
            set?.let {
                filteredMapOf(
                    MARKETING to consentIf(it.contains(Subscription.Channels.Messaging.MARKETING)),
                    TRANSACTIONAL to consentIf(
                        it.contains(Subscription.Channels.Messaging.TRANSACTIONAL)
                    )
                )
            } ?: emptyMap()

        private fun consentIf(selected: Boolean): Map<String, String> =
            if (selected) subscribedConsent else emptyMap()
    }

    override var type: String = "Create Subscription"

    override var query: Map<String, String> = mapOf(
        COMPANY_ID to Registry.config.apiKey
    )

    override val successCodes: IntRange get() = HTTP_ACCEPTED..HTTP_ACCEPTED

    constructor(subscription: Subscription, profile: Profile) : this() {
        body = jsonMapOf(*formatBody(subscription, profile))
    }
}

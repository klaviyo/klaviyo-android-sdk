package com.klaviyo.analytics.model

/**
 * Represents a request to subscribe the current profile to a Klaviyo list, with optional
 * per-channel marketing/transactional consent.
 *
 * Consent is expressed through [channels]. A `null` [channels] value grants the server's default
 * of MARKETING consent on every channel the profile has an identifier for, and is only reachable
 * through [allAvailableMarketing] — the public constructor requires [channels] so a broad grant is
 * never the result of an omitted argument.
 */
class Subscription private constructor(
    /**
     * ID of the Klaviyo list to subscribe the profile to.
     */
    val listId: String,

    /**
     * Optional signup-source label stored as the consent record's `$source`. Omitted from the
     * request when `null`.
     */
    val customSource: String?,

    /**
     * Channels and consent sub-types to request, or `null` to defer to the server default
     * (see [allAvailableMarketing]).
     */
    val channels: Channels?
) {

    /**
     * Creates a subscription request for the given [channels].
     *
     * @param listId ID of the Klaviyo list to subscribe the profile to.
     * @param channels Channels and consent sub-types to request consent for.
     * @param customSource Optional signup-source label. Omitted from the request when `null`.
     */
    @JvmOverloads
    constructor(
        listId: String,
        channels: Channels,
        customSource: String? = null
        // Private ctor params are ordered (listId, customSource, channels) so its JVM signature
        // doesn't clash with this constructor's.
    ) : this(listId, customSource, channels)

    /**
     * Channels and consent sub-types to request. Mirrors the API's `subscriptions` object: each
     * channel exposes only the consent sub-types the API supports for it, so invalid combinations
     * (transactional email, open-tracking SMS) cannot be expressed.
     */
    class Channels @JvmOverloads constructor(
        /**
         * Consent sub-types to request on the EMAIL channel, or `null` to leave EMAIL untouched.
         */
        val email: Set<Email>? = null,

        /**
         * Consent sub-types to request on the SMS channel, or `null` to leave SMS untouched.
         */
        val sms: Set<Messaging>? = null,

        /**
         * Consent sub-types to request on the WhatsApp channel, or `null` to leave WhatsApp untouched.
         */
        val whatsapp: Set<Messaging>? = null
    ) {
        /**
         * Consent sub-types supported on the EMAIL channel.
         */
        enum class Email { MARKETING, OPEN_TRACKING }

        /**
         * Consent sub-types supported on the SMS and WhatsApp channels.
         */
        enum class Messaging { MARKETING, TRANSACTIONAL }
    }

    companion object {
        /**
         * Creates a subscription that grants MARKETING consent on every channel the profile has an
         * identifier for (email → email marketing, phone → SMS marketing). Mirrors the server's
         * default behavior when no consent object is sent, but requesting it is a deliberate call.
         *
         * @param listId ID of the Klaviyo list to subscribe the profile to.
         * @param customSource Optional signup-source label. Omitted from the request when `null`.
         */
        @JvmStatic
        @JvmOverloads
        fun allAvailableMarketing(listId: String, customSource: String? = null): Subscription =
            Subscription(listId, customSource, null)
    }
}

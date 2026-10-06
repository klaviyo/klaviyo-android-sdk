package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.ImmutableProfile

/**
 * How a change of profile identifiers relates the previous profile to the next one.
 */
internal enum class ProfileTransition {
    /** External ID, email, phone number and anonymous ID are all equal. */
    UNCHANGED,

    /**
     * At least one of external ID, email or phone number is present and equal on both sides, and
     * none is present on both sides with different values. The anonymous ID is ignored.
     */
    COMPATIBLE,

    /** Any other change. */
    REPLACEMENT
}

/**
 * External ID, email, phone number and anonymous ID of a profile, in the form state stores them:
 * trimmed, with blank values treated as absent. Values are compared case-sensitively.
 */
internal class ProfileIdentifiers(
    externalId: String?,
    email: String?,
    phoneNumber: String?,
    anonymousId: String?
) {
    val externalId: String? = normalize(externalId)
    val email: String? = normalize(email)
    val phoneNumber: String? = normalize(phoneNumber)
    val anonymousId: String? = normalize(anonymousId)

    /** True when external ID, email or phone number is present. */
    val isIdentified: Boolean get() = identifiers.any { it != null }

    /** External ID, email and phone number, in that order. */
    val identifiers: List<String?> = listOf(this.externalId, this.email, this.phoneNumber)

    private companion object {
        fun normalize(value: String?): String? = value?.trim()?.ifEmpty { null }
    }
}

internal val ImmutableProfile.profileIdentifiers: ProfileIdentifiers
    get() = ProfileIdentifiers(externalId, email, phoneNumber, anonymousId)

internal val State.profileIdentifiers: ProfileIdentifiers
    get() = ProfileIdentifiers(externalId, email, phoneNumber, anonymousId)

/**
 * Classify the change of profile identifiers from [previous] to [next].
 */
internal fun classify(previous: ProfileIdentifiers, next: ProfileIdentifiers): ProfileTransition {
    if (previous.identifiers == next.identifiers && previous.anonymousId == next.anonymousId) {
        return ProfileTransition.UNCHANGED
    }

    val shared = previous.identifiers.zip(next.identifiers)
        .filter { (old, new) -> old != null && new != null }

    return if (shared.isNotEmpty() && shared.all { (old, new) -> old == new }) {
        ProfileTransition.COMPATIBLE
    } else {
        ProfileTransition.REPLACEMENT
    }
}

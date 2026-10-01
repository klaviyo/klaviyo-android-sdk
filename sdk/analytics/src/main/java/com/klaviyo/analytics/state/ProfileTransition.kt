package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.model.ProfileKey.ANONYMOUS_ID
import com.klaviyo.analytics.model.ProfileKey.EMAIL
import com.klaviyo.analytics.model.ProfileKey.EXTERNAL_ID
import com.klaviyo.analytics.model.ProfileKey.PHONE_NUMBER

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

    /**
     * Copy with the identifier for [key] set to [value].
     * Returns an equal copy for keys that are not identifiers.
     */
    fun withIdentifier(key: ProfileKey, value: String?): ProfileIdentifiers = when (key) {
        EXTERNAL_ID -> ProfileIdentifiers(value, email, phoneNumber, anonymousId)
        EMAIL -> ProfileIdentifiers(externalId, value, phoneNumber, anonymousId)
        PHONE_NUMBER -> ProfileIdentifiers(externalId, email, value, anonymousId)
        ANONYMOUS_ID -> ProfileIdentifiers(externalId, email, phoneNumber, value)
        else -> ProfileIdentifiers(externalId, email, phoneNumber, anonymousId)
    }

    /** External ID, email and phone number, in that order. */
    val identifiers: List<String?> get() = listOf(externalId, email, phoneNumber)

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

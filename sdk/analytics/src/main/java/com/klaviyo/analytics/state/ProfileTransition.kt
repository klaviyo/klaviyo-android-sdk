package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.model.ProfileKey.EMAIL
import com.klaviyo.analytics.model.ProfileKey.EXTERNAL_ID
import com.klaviyo.analytics.model.ProfileKey.PHONE_NUMBER

/**
 * How a change of profile identifiers relates the previous profile to the next one.
 */
internal enum class ProfileTransition {
    /** All identifiers are equal. */
    UNCHANGED,

    /**
     * At least one identifier is present on both sides, every such identifier is equal,
     * and the rest were only added or removed.
     */
    COMPATIBLE,

    /** An identifier present on both sides differs, or no identifier is present on both sides. */
    REPLACEMENT
}

/**
 * External ID, email and phone number of a profile, each trimmed with blank values treated as
 * absent. Values are compared case-sensitively. The anonymous ID is not included.
 */
internal class ProfileIdentifiers(
    externalId: String?,
    email: String?,
    phoneNumber: String?
) {
    val externalId: String? = normalize(externalId)
    val email: String? = normalize(email)
    val phoneNumber: String? = normalize(phoneNumber)

    private val values: List<String?> get() = listOf(externalId, email, phoneNumber)

    /** True when any identifier is present. */
    val isIdentified: Boolean get() = values.any { it != null }

    /**
     * Copy with the identifier for [key] set to [value].
     * Returns an equal copy for keys that are not external ID, email or phone number.
     */
    fun withIdentifier(key: ProfileKey, value: String?): ProfileIdentifiers = when (key) {
        EXTERNAL_ID -> ProfileIdentifiers(value, email, phoneNumber)
        EMAIL -> ProfileIdentifiers(externalId, value, phoneNumber)
        PHONE_NUMBER -> ProfileIdentifiers(externalId, email, value)
        else -> ProfileIdentifiers(externalId, email, phoneNumber)
    }

    /** Classify the change from these identifiers to [next]. */
    fun transitionTo(next: ProfileIdentifiers): ProfileTransition {
        if (values == next.values) return ProfileTransition.UNCHANGED

        val shared = values.zip(next.values).filter { (old, new) -> old != null && new != null }

        return if (shared.isNotEmpty() && shared.all { (old, new) -> old == new }) {
            ProfileTransition.COMPATIBLE
        } else {
            ProfileTransition.REPLACEMENT
        }
    }

    private companion object {
        fun normalize(value: String?): String? = value?.trim()?.ifEmpty { null }
    }
}

internal val ImmutableProfile.profileIdentifiers: ProfileIdentifiers
    get() = ProfileIdentifiers(externalId, email, phoneNumber)

internal val State.profileIdentifiers: ProfileIdentifiers
    get() = ProfileIdentifiers(externalId, email, phoneNumber)

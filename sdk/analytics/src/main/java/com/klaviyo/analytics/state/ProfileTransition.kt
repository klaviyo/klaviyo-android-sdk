package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Profile

internal enum class ProfileTransition {
    Unchanged,
    Compatible,
    Replacement
}

internal fun State.profileTransition(profile: Profile): ProfileTransition {
    val current = listOf(externalId, email, phoneNumber).map(::normalizedIdentifier)
    val incoming = listOf(profile.externalId, profile.email, profile.phoneNumber)
        .map(::normalizedIdentifier)
    if (current == incoming) return ProfileTransition.Unchanged

    val sharedIdentifiers = current.zip(incoming).filter { (existing, next) ->
        existing != null && next != null
    }
    return when {
        sharedIdentifiers.any { (existing, next) -> existing != next } ->
            ProfileTransition.Replacement
        sharedIdentifiers.isNotEmpty() -> ProfileTransition.Compatible
        else -> ProfileTransition.Replacement
    }
}

private fun normalizedIdentifier(value: String?): String? = value?.trim()?.ifEmpty { null }

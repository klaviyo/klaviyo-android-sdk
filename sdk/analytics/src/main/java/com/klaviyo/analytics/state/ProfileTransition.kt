package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Profile

enum class ProfileTransition {
    Unchanged,
    Compatible,
    Replacement
}

internal fun State.profileTransition(profile: Profile): ProfileTransition {
    val current = listOf(externalId, email, phoneNumber).map(::normalizedIdentifier)
    return classifyProfileTransition(current, profile.identifiers())
}

fun Profile.profileTransition(next: Profile): ProfileTransition =
    classifyProfileTransition(identifiers(), next.identifiers())

private fun Profile.identifiers(): List<String?> =
    listOf(externalId, email, phoneNumber).map(::normalizedIdentifier)

private fun classifyProfileTransition(
    current: List<String?>,
    incoming: List<String?>
): ProfileTransition {
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

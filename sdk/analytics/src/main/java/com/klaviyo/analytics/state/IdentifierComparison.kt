package com.klaviyo.analytics.state

/**
 * Results of comparing each identifier that has a value on both [this] and [other]. Emails are
 * compared without regard to case, external IDs and phone numbers exactly.
 */
private fun ProfileIdentifiers.compare(other: ProfileIdentifiers): List<Boolean> = listOfNotNull(
    externalId?.let { old -> other.externalId?.let { old == it } },
    email?.let { old -> other.email?.let { old.equals(it, ignoreCase = true) } },
    phoneNumber?.let { old -> other.phoneNumber?.let { old == it } }
)

/**
 * True when an identifier has a value on both profiles and the values differ
 */
internal fun ProfileIdentifiers.conflictsWith(other: ProfileIdentifiers): Boolean =
    compare(other).any { !it }

/**
 * True when at least one identifier has a value on both profiles and none of them differ
 */
internal fun ProfileIdentifiers.sharesIdentifierWith(other: ProfileIdentifiers): Boolean =
    compare(other).let { results -> results.isNotEmpty() && results.all { it } }

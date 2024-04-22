package com.klaviyo.analytics.model

/**
 * Immutable implementation of [Profile] model to support observability
 */
interface ImmutableProfile {
    val externalId: String?
    val email: String?
    val phoneNumber: String?
    val anonymousId: String?
    val attributes: Profile

    fun copy(): Profile
}

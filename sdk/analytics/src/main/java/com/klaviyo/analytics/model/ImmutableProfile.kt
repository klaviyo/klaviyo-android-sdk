package com.klaviyo.analytics.model

import java.io.Serializable

/**
 * Immutable implementation of [Profile] model to support observability and prevent untracked mutations
 */
interface ImmutableProfile {
    val externalId: String?
    val email: String?
    val phoneNumber: String?
    val anonymousId: String?

    operator fun get(key: ProfileKey): Serializable?

    fun copy(): Profile
}

package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.API_KEY
import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.analytics.model.Keyword
import com.klaviyo.analytics.model.PROFILE_ATTRIBUTES
import com.klaviyo.analytics.model.ProfileKey

sealed interface StateChange {
    val key: Keyword?
    val oldValue: Any?

    /**
     * Emitted when the company ID aka public API key changes in state
     */
    data class ApiKey(
        override val oldValue: String?
    ) : StateChange {
        override val key: Keyword = API_KEY
    }

    /**
     * Emitted whenever a profile identifier changes in state, except for a full profile reset.
     */
    data class ProfileIdentifier(
        override val key: ProfileKey,
        override val oldValue: String?
    ) : StateChange

    /**
     * Emitted when the profile attributes (any non-identifier properties) change in state
     */
    data class ProfileAttributes(
        override val oldValue: ImmutableProfile?
    ) : StateChange {
        override val key: Keyword = PROFILE_ATTRIBUTES
    }

    /**
     * Emitted on profile reset
     */
    data class ProfileReset(
        override val oldValue: ImmutableProfile
    ) : StateChange {
        override val key: Keyword? = null
    }

    /**
     * Catch-all change to a value in state
     */
    data class KeyValue(
        override val key: Keyword,
        override val oldValue: String?
    ) : StateChange
}

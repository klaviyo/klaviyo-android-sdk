package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.analytics.model.Keyword
import com.klaviyo.analytics.model.ProfileKey

sealed class StateChange() {
    /**
     * Emitted when the company ID aka public API key changes in state
     */
    data class ApiKey(
        val oldValue: String?
    ) : StateChange()

    /**
     * Emitted whenever a profile identifier changes in state, except for a full profile reset.
     */
    data class ProfileIdentifier(
        val key: ProfileKey,
        val oldValue: String?
    ) : StateChange()

    /**
     * Emitted when the profile attributes (any non-identifier properties) change in state
     */
    data class ProfileAttributes(
        val oldValue: ImmutableProfile?
    ) : StateChange()

    /**
     * Emitted on profile reset
     */
    data class ProfileReset(
        val oldValue: ImmutableProfile
    ) : StateChange()

    /**
     * Catch-all change to a value in state
     */
    data class KeyValue(
        val key: Keyword,
        val oldValue: String?
    ) : StateChange()
}

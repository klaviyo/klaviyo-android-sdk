package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.analytics.model.Keyword
import com.klaviyo.analytics.model.ProfileKey

sealed class StateChange() {
    data class ApiKey(
        val oldValue: String?
    ) : StateChange()

    data class ProfileIdentifier(
        val key: ProfileKey,
        val oldValue: String?
    ) : StateChange()

    data class ProfileAttributes(
        val oldValue: ImmutableProfile?
    ) : StateChange()

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

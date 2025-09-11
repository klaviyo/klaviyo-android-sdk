package com.klaviyo.analytics.model

import com.klaviyo.core.model.Keyword

sealed class StateKey(name: String) : Keyword(name) {
    /**
     * Key in state for storing the company ID aka public API key
     */
    object API_KEY : StateKey("api_key")

    /**
     * Key in state for storing the latest push status
     */
    internal object PUSH_STATE : StateKey("push_state")
}

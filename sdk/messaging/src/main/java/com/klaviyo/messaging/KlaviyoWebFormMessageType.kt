package com.klaviyo.messaging

import com.klaviyo.analytics.model.Event

/**
 * This should be updated with any new message types we add coming from the onsite-in-app-forms
 */
sealed class KlaviyoWebFormMessageType {
    data object Show : KlaviyoWebFormMessageType()
    data object Close : KlaviyoWebFormMessageType()
    data class Console(
        val consoleLog: String,
        val level: String? = null
    ) : KlaviyoWebFormMessageType()
    data class ProfileEvent(
        val event: Event
    ) : KlaviyoWebFormMessageType()
}

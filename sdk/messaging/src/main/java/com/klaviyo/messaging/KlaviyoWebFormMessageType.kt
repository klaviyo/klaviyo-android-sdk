package com.klaviyo.messaging

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.networking.requests.AggregateEventPayload

/**
 * This should be updated with any new message types we add coming from the onsite-in-app-forms
 */
sealed class KlaviyoWebFormMessageType {
    data object Show : KlaviyoWebFormMessageType()

    data object Close : KlaviyoWebFormMessageType()

    data class ProfileEvent(
        val event: Event
    ) : KlaviyoWebFormMessageType()

    data class AggregateEventTracked(
        val payload: AggregateEventPayload
    ) : KlaviyoWebFormMessageType()
}

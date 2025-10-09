package com.klaviyo.analytics.model

/**
 * All event property keys recognised by the Klaviyo APIs
 * Custom properties can be defined using the [CUSTOM] inner class
 */
sealed class EventKey(name: String) : Keyword(name) {
    object EVENT_ID : EventKey("\$event_id")
    object VALUE : EventKey("\$value")

    /**
     * For [EventMetric.OPENED_PUSH] events, append the device token as an event property
     */
    internal object PUSH_TOKEN : EventKey("push_token")

    /**
     * Internal timestamp property added to events when they are queued
     */
    internal object TIME : EventKey("_time")

    class CUSTOM(propertyName: String) : EventKey(propertyName)
}

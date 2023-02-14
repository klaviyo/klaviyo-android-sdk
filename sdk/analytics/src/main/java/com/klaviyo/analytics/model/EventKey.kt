package com.klaviyo.analytics.model

/**
 * All event property keys recognised by the Klaviyo APIs
 * Custom properties can be defined using the [CUSTOM] inner class
 */
sealed class EventKey(name: String) : Keyword(name) {
    object EVENT_ID : EventKey("\$event_id")
    object VALUE : EventKey("\$value")

    class CUSTOM(propertyName: String) : EventKey(propertyName)
}

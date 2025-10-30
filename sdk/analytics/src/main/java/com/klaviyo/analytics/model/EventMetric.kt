package com.klaviyo.analytics.model

/**
 * Common clientside event metrics recognized by Klaviyo
 * Custom metrics can be defined with the [CUSTOM] inner class
 *
 * @property name String that represents the name of the metric
 */
sealed class EventMetric(name: String) : Keyword(name) {
    internal object OPENED_PUSH : EventMetric("\$opened_push")

    object OPENED_APP : EventMetric("Opened App")
    object VIEWED_PRODUCT : EventMetric("Viewed Product")
    object ADDED_TO_CART : EventMetric("Added to Cart")
    object STARTED_CHECKOUT : EventMetric("Started Checkout")

    open class CUSTOM(name: String) : EventMetric(name)

    /**
     * Key Klaviyo event metrics are prefixed with a '$' character, these may
     * treated with higher priority and subjected to special validation.
     */
    internal val isKlaviyoMetric: Boolean = this.name.startsWith("$")
}

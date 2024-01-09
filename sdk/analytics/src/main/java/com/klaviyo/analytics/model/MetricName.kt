package com.klaviyo.analytics.model

/**
 * Common clientside event metrics recognized by Klaviyo
 * Custom metrics can be defined with the [CUSTOM] inner class
 *
 * @property name String that represents the name of the metric
 */
sealed class MetricName(name: String) : Keyword(name) {
    internal object OPENED_PUSH : MetricName("\$opened_push")

    object OPENED_APP : MetricName("Opened App")
    object VIEWED_PRODUCT : MetricName("Viewed Product")
    object ADDED_TO_CART : MetricName("Added to Cart")
    object STARTED_CHECKOUT : MetricName("Started Checkout")

    class CUSTOM(name: String) : MetricName(name)
}

/**
 * Events recognized by Klaviyo (Deprecated)
 * Custom events can be defined using the [CUSTOM] inner class
 *
 * @property name String value of the event which is recognized by Klaviyo as a registered event
 */
@Deprecated(
    """
    These metric names were erroneously provided in the first version of the SDK. 
    The keywords are spelled incorrectly, and many of are intended to be used serverside only.
    To better match Klaviyo's on-site integrations, the metric keywords have been corrected. 
    Use MetricName for corrected values, or use MetricName.CUSTOM to define other metric names.
    
    EventType will be removed in the next major release. 
""",
    replaceWith = ReplaceWith("com.klaviyo.analytics.model.MetricName")
)
sealed class EventType(name: String) : MetricName(name) {

    // Push-related
    internal object OPENED_PUSH : EventType(MetricName.OPENED_PUSH.name)

    // Product viewing events
    object VIEWED_PRODUCT : EventType("\$viewed_product")
    object SEARCHED_PRODUCTS : EventType("\$searched_products")

    // Checkout events
    object STARTED_CHECKOUT : EventType("\$started_checkout")

    // Order events
    object PLACED_ORDER : EventType("\$placed_order")
    object ORDERED_PRODUCT : EventType("\$ordered_product")
    object CANCELLED_ORDER : EventType("\$cancelled_order")
    object REFUNDED_ORDER : EventType("\$refunded_order")
    object PAID_FOR_ORDER : EventType("\$paid_for_order")

    // Subscription events
    object SUBSCRIBED_TO_BACK_IN_STOCK : EventType("\$subscribed_to_back_in_stock")
    object SUBSCRIBED_TO_COMING_SOON : EventType("\$subscribed_to_coming_soon")
    object SUBSCRIBED_TO_LIST : EventType("\$subscribed_to_list")

    // Payment events
    object SUCCESSFUL_PAYMENT : EventType("\$successful_payment")
    object FAILED_PAYMENT : EventType("\$failed_payment")

    // Custom events
    class CUSTOM(eventName: String) : EventType(eventName)
}

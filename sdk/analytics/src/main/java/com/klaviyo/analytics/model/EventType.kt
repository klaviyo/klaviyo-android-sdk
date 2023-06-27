package com.klaviyo.analytics.model

/**
 * Events recognized by Klaviyo
 * Custom events can be defined using the [CUSTOM] inner class
 *
 * @property name String value of the event which is recognized by Klaviyo as a registered event
 */
sealed class EventType(name: String) : Keyword(name) {

    // Push-related
    internal object OPENED_PUSH : EventType("\$opened_push")

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

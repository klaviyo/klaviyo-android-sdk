package com.klaviyo.coresdk.networking

/**
 * Events recognized by Klaviyo
 * Custom events can be defined using the [CUSTOM_EVENT] inner class
 *
 * @property name String value of the event which is recognized by Klaviyo as a registered event
 */
sealed class KlaviyoEvent(val name: String) {
    // Product viewing events
    object VIEWED_PRODUCT: KlaviyoEvent("\$viewed_product")
    object SEARCHED_PRODUCTS: KlaviyoEvent("\$searched_products")

    // Checkout events
    object STARTED_CHECKOUT: KlaviyoEvent("\$started_checkout")

    // Order events
    object PLACED_ORDER: KlaviyoEvent("\$placed_order")
    object ORDERED_PRODUCT: KlaviyoEvent("\$ordered_product")
    object CANCELLED_ORDER: KlaviyoEvent("\$cancelled_order")
    object REFUNDED_ORDER: KlaviyoEvent("\$refunded_order")
    object PAID_FOR_ORDER: KlaviyoEvent("\$paid_for_order")

    // Order complete events
    object FULFILLED_ORDER: KlaviyoEvent("\$fulfilled_order")
    object FULLFILLED_SHIPMENT: KlaviyoEvent("\$fulfilled_shipment")
    object FULFILLED_PRODUCT: KlaviyoEvent("\$fulfilled_product")
    object COMPLETED_ORDER: KlaviyoEvent("\$completed_order")
    object SHIPPED_ORDER: KlaviyoEvent("\$shipped_order")

    // Subscription events
    object SUBSCRIBED_TO_BACK_IN_STOCK: KlaviyoEvent("\$subscribed_to_back_in_stock")
    object SUBSCRIBED_TO_COMING_SOON: KlaviyoEvent("\$subscribed_to_coming_soon")
    object SUBSCRIBED_TO_LIST: KlaviyoEvent("\$subscribed_to_list")

    // Payment events
    object SUCCESSFUL_PAYMENT: KlaviyoEvent("\$successful_payment")
    object FAILED_PAYMENT: KlaviyoEvent("\$failed_payment")
    object REFUNDED_PAYMENT: KlaviyoEvent("\$refunded_payment")
    object ISSUED_INVOICE: KlaviyoEvent("\$issued_invoice")
    object CREATED_SUBSCRIPTION: KlaviyoEvent("\$created_subscription")
    object ACTIVATED_SUBSCRIPTION: KlaviyoEvent("\$activated_subscription")
    object CANCELLED_SUBSCRIPTION: KlaviyoEvent("\$cancelled_subscription")
    object EXPIRED_SUBSCRIPTION: KlaviyoEvent("\$expired_subscription")
    object CLOSED_SUBSCRIPTION: KlaviyoEvent("\$closed_subscription")

    // Custom events
    class CUSTOM_EVENT(eventName: String): KlaviyoEvent(eventName)
}

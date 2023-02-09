/**
 * Models namespace
 */
package com.klaviyo.coresdk.model

import com.klaviyo.coresdk.model.KlaviyoEventType.CUSTOM

/**
 * Base class used to provide polymorphic properties to the use of profile and event keys
 */
abstract class KlaviyoKeyword(val name: String) {
    override fun toString(): String = name
    override fun equals(other: Any?): Boolean = (other as? KlaviyoKeyword).toString() == toString()
    override fun hashCode(): Int = name.hashCode()
}

/**
 * All profile property keys recognised by the Klaviyo APIs
 * Custom properties can be defined using the [CUSTOM] inner class
 */
sealed class KlaviyoProfileAttributeKey(name: String) : KlaviyoKeyword(name) {

    // Identifiers
    object EXTERNAL_ID : KlaviyoProfileAttributeKey("\$external_id")
    object EMAIL : KlaviyoProfileAttributeKey("\$email")
    object PHONE_NUMBER : KlaviyoProfileAttributeKey("\$phone_number")
    internal object ANONYMOUS : KlaviyoProfileAttributeKey("\$anonymous")

    // Personal information
    object FIRST_NAME : KlaviyoProfileAttributeKey("\$first_name")
    object LAST_NAME : KlaviyoProfileAttributeKey("\$last_name")
    object TITLE : KlaviyoProfileAttributeKey("\$title")
    object ORGANIZATION : KlaviyoProfileAttributeKey("\$organization")
    object CITY : KlaviyoProfileAttributeKey("\$city")
    object REGION : KlaviyoProfileAttributeKey("\$region")
    object COUNTRY : KlaviyoProfileAttributeKey("\$country")
    object ZIP : KlaviyoProfileAttributeKey("\$zip")
    object IMAGE : KlaviyoProfileAttributeKey("\$image")
    object CONSENT : KlaviyoProfileAttributeKey("\$consent")

    // Custom properties
    class CUSTOM(propertyName: String) : KlaviyoProfileAttributeKey(propertyName)

    // Other
    internal object APPEND : KlaviyoProfileAttributeKey("\$append")
}

/**
 * Events recognized by Klaviyo
 * Custom events can be defined using the [CUSTOM] inner class
 *
 * @property name String value of the event which is recognized by Klaviyo as a registered event
 */
sealed class KlaviyoEventType(name: String) : KlaviyoKeyword(name) {

    // Push-related
    object OPENED_PUSH : KlaviyoEventType("\$opened_push")

    // Product viewing events
    object VIEWED_PRODUCT : KlaviyoEventType("\$viewed_product")
    object SEARCHED_PRODUCTS : KlaviyoEventType("\$searched_products")

    // Checkout events
    object STARTED_CHECKOUT : KlaviyoEventType("\$started_checkout")

    // Order events
    object PLACED_ORDER : KlaviyoEventType("\$placed_order")
    object ORDERED_PRODUCT : KlaviyoEventType("\$ordered_product")
    object CANCELLED_ORDER : KlaviyoEventType("\$cancelled_order")
    object REFUNDED_ORDER : KlaviyoEventType("\$refunded_order")
    object PAID_FOR_ORDER : KlaviyoEventType("\$paid_for_order")

    // Subscription events
    object SUBSCRIBED_TO_BACK_IN_STOCK : KlaviyoEventType("\$subscribed_to_back_in_stock")
    object SUBSCRIBED_TO_COMING_SOON : KlaviyoEventType("\$subscribed_to_coming_soon")
    object SUBSCRIBED_TO_LIST : KlaviyoEventType("\$subscribed_to_list")

    // Payment events
    object SUCCESSFUL_PAYMENT : KlaviyoEventType("\$successful_payment")
    object FAILED_PAYMENT : KlaviyoEventType("\$failed_payment")

    // Custom events
    class CUSTOM(eventName: String) : KlaviyoEventType(eventName)
}

/**
 * All event property keys recognised by the Klaviyo APIs
 * Custom properties can be defined using the [CUSTOM] inner class
 */
sealed class KlaviyoEventAttributeKey(name: String) : KlaviyoKeyword(name) {
    object EVENT_ID : KlaviyoEventAttributeKey("\$event_id")
    object VALUE : KlaviyoEventAttributeKey("\$value")

    class CUSTOM(propertyName: String) : KlaviyoEventAttributeKey(propertyName)
}

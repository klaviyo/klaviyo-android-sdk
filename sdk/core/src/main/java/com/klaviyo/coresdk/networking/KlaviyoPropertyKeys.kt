package com.klaviyo.coresdk.networking

/**
 * Base class used to provide polymorphic properties to the use of customer and event keys
 */
sealed class KlaviyoPropertyKeys(val name: String)

/**
 * All keys recognised by the Klaviyo APIs for identifying information within maps of customer properties
 */
sealed class KlaviyoCustomerPropertyKeys(name: String): KlaviyoPropertyKeys(name) {

    // Identifiers
    object ID: KlaviyoCustomerPropertyKeys("\$id")
    object EMAIL: KlaviyoCustomerPropertyKeys("\$email")
    object PHONE_NUMBER: KlaviyoCustomerPropertyKeys("\$phone_number")
    internal object ANONYMOUS_ID: KlaviyoCustomerPropertyKeys("\$anonymous")

    // Personal information
    object FIRST_NAME: KlaviyoCustomerPropertyKeys("\$first_name")
    object LAST_NAME: KlaviyoCustomerPropertyKeys("\$last_name")
    object TITLE: KlaviyoCustomerPropertyKeys("\$title")
    object ORGANIZATION: KlaviyoCustomerPropertyKeys("\$organization")
    object CITY: KlaviyoCustomerPropertyKeys("\$city")
    object REGION: KlaviyoCustomerPropertyKeys("\$region")
    object COUNTRY: KlaviyoCustomerPropertyKeys("\$country")
    object ZIP_CODE: KlaviyoCustomerPropertyKeys("\$zip")
    object IMAGE: KlaviyoCustomerPropertyKeys("\$image")
    object CONSENT: KlaviyoCustomerPropertyKeys("\$consent")

    // Custom properties
    class CUSTOM(propertyName: String): KlaviyoCustomerPropertyKeys(propertyName)

    // Other
    internal object APPEND: KlaviyoCustomerPropertyKeys("\$append")
}

/**
 * All keys recognised by the Klaviyo APIs for identifying information within maps of event properties
 */
sealed class KlaviyoEventPropertyKeys(name: String): KlaviyoPropertyKeys(name) {
    object EVENT_ID: KlaviyoEventPropertyKeys("\$event_id")
    object VALUE: KlaviyoEventPropertyKeys("\$value")

    class CUSTOM(propertyName: String): KlaviyoEventPropertyKeys(propertyName)
}
package com.klaviyo.coresdk.networking

/**
 * Base class used to provide polymorphic properties to the use of customer and event keys
 */
sealed class KlaviyoPropertyKeys(val name: String)

/**
 * All keys recognised by the Klaviyo APIs for identifying information within maps of customer properties
 */
sealed class KlaviyoCustomerPropKeys(name: String): KlaviyoPropertyKeys(name) {

    // Identifiers
    object ID: KlaviyoCustomerPropKeys("\$id")
    object EMAIL: KlaviyoCustomerPropKeys("\$email")
    object PHONE_NUMBER: KlaviyoCustomerPropKeys("\$phone_number")
    object ANONYMOUS_ID: KlaviyoCustomerPropKeys("\$anonymous_id")

    // Personal information
    object FIRST_NAME: KlaviyoCustomerPropKeys("\$first_name")
    object LAST_NAME: KlaviyoCustomerPropKeys("\$last_name")
    object TITLE: KlaviyoCustomerPropKeys("\$title")
    object ORGANIZATION: KlaviyoCustomerPropKeys("\$organization")
    object CITY: KlaviyoCustomerPropKeys("\$city")
    object REGION: KlaviyoCustomerPropKeys("\$region")
    object COUNTRY: KlaviyoCustomerPropKeys("\$country")
    object ZIP_CODE: KlaviyoCustomerPropKeys("\$zip")
    object IMAGE: KlaviyoCustomerPropKeys("\$image")
    object CONSENT: KlaviyoCustomerPropKeys("\$consent")

    // Custom properties
    class CUSTOM(propertyName: String): KlaviyoCustomerPropKeys(propertyName)

    // Other
    internal object APPENDED: KlaviyoCustomerPropKeys("\$append")
}

/**
 * All keys recognised by the Klaviyo APIs for identifying information within maps of event properties
 */
sealed class KlaviyoEventPropKeys(name: String): KlaviyoPropertyKeys(name) {
    object EVENT_ID: KlaviyoEventPropKeys("\$event_id")
    object VALUE: KlaviyoEventPropKeys("\$value")

    class CUSTOM(propertyName: String): KlaviyoEventPropKeys(propertyName)
}
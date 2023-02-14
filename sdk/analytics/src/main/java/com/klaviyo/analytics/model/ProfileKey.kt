package com.klaviyo.analytics.model

/**
 * All profile property keys recognised by the Klaviyo APIs
 * Custom properties can be defined using the [CUSTOM] inner class
 */
sealed class ProfileKey(name: String) : Keyword(name) {

    // Identifiers
    object EXTERNAL_ID : ProfileKey("\$external_id")
    object EMAIL : ProfileKey("\$email")
    object PHONE_NUMBER : ProfileKey("\$phone_number")
    internal object ANONYMOUS : ProfileKey("\$anonymous")

    // Personal information
    object FIRST_NAME : ProfileKey("\$first_name")
    object LAST_NAME : ProfileKey("\$last_name")
    object TITLE : ProfileKey("\$title")
    object ORGANIZATION : ProfileKey("\$organization")
    object CITY : ProfileKey("\$city")
    object REGION : ProfileKey("\$region")
    object COUNTRY : ProfileKey("\$country")
    object ZIP : ProfileKey("\$zip")
    object IMAGE : ProfileKey("\$image")
    object CONSENT : ProfileKey("\$consent")

    // Custom properties
    class CUSTOM(propertyName: String) : ProfileKey(propertyName)

    // Other
    internal object APPEND : ProfileKey("\$append")
}

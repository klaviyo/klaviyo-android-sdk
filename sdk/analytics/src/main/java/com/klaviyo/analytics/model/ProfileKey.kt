package com.klaviyo.analytics.model

/**
 * All profile attributes recognised by the Klaviyo APIs
 * Custom properties can be defined using the [CUSTOM] inner class
 */
sealed class ProfileKey(name: String) : Keyword(name) {

    // Identifiers
    internal object EXTERNAL_ID : ProfileKey("external_id")
    internal object EMAIL : ProfileKey("email")
    internal object PHONE_NUMBER : ProfileKey("phone_number")
    internal object ANONYMOUS_ID : ProfileKey("anonymous_id")
    internal object PUSH_TOKEN : ProfileKey("push_token")
    internal object PUSH_STATE : ProfileKey("push_state")
    internal object API_KEY : ProfileKey("api_key")

    // Personal information
    object FIRST_NAME : ProfileKey("first_name")
    object LAST_NAME : ProfileKey("last_name")
    object ORGANIZATION : ProfileKey("organization")
    object TITLE : ProfileKey("title")
    object IMAGE : ProfileKey("image")

    // Location attributes
    object ADDRESS1 : ProfileKey("address1")
    object ADDRESS2 : ProfileKey("address2")
    object CITY : ProfileKey("city")
    object COUNTRY : ProfileKey("country")
    object LATITUDE : ProfileKey("latitude")
    object LONGITUDE : ProfileKey("longitude")
    object REGION : ProfileKey("region")
    object ZIP : ProfileKey("zip")
    object TIMEZONE : ProfileKey("timezone")

    // Custom properties
    class CUSTOM(propertyName: String) : ProfileKey(propertyName)
}

internal object PROFILE_ATTRIBUTES : Keyword("attributes")

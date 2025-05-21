package com.klaviyo.analytics.model

/**
 * All profile attributes recognised by the Klaviyo APIs
 * Custom properties can be defined using the [CUSTOM] inner class
 */
sealed class ProfileKey(name: String) : Keyword(name) {

    // Identifiers
    // Note: it best to use the explicit setter method for each identifier
    internal object EXTERNAL_ID : ProfileKey("external_id")
    internal object EMAIL : ProfileKey("email")
    internal object PHONE_NUMBER : ProfileKey("phone_number")
    internal object ANONYMOUS_ID : ProfileKey("anonymous_id")

    // Push properties
    internal object PUSH_TOKEN : ProfileKey("push_token")
    internal object PUSH_STATE : ProfileKey("push_state")

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

    companion object {
        /**
         * List of keys that are considered profile identifiers
         *
         * Note: it best to use the explicit setter method for each identifier
         */
        val IDENTIFIERS by lazy {
            setOf(
                EXTERNAL_ID.name,
                EMAIL.name,
                PHONE_NUMBER.name,
                ANONYMOUS_ID.name
            )
        }
    }
}

internal object PROFILE_ATTRIBUTES : Keyword("attributes")

internal object API_KEY : Keyword("api_key")

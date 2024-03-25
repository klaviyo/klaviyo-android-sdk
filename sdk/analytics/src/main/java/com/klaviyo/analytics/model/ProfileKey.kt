package com.klaviyo.analytics.model

/**
 * All profile attributes recognised by the Klaviyo APIs
 * Custom properties can be defined using the [CUSTOM] inner class
 */
sealed class ProfileKey(name: String) : Keyword(name) {

    // Identifiers
    object EXTERNAL_ID : ProfileKey("external_id")
    object EMAIL : ProfileKey("email")
    object PHONE_NUMBER : ProfileKey("phone_number")
    internal object ANONYMOUS_ID : ProfileKey("anonymous_id")
    internal object PUSH_TOKEN : ProfileKey("push_token")
    internal object PUSH_STATE : ProfileKey("push_state")

    // Personal information
    object FIRST_NAME : ProfileKey("first_name")
    object LAST_NAME : ProfileKey("last_name")
    object ORGANIZATION : ProfileKey("organization")
    object TITLE : ProfileKey("title")
    object IMAGE : ProfileKey("image")

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

    /**
     * Helper method to translate certain keys to their dollar-prefixed key
     * This only applies to the identifier keys under certain circumstances
     *
     * @return
     */
    internal fun specialKey(): String = when (this) {
        ANONYMOUS_ID -> "\$anonymous"
        EXTERNAL_ID -> "\$id"
        EMAIL, PHONE_NUMBER -> "$$name"
        else -> name
    }
}

package com.klaviyo.coresdk.model

import java.io.Serializable

/**
 * Controls the data that can be input into a map of profile attributes recognised by Klaviyo
 */
class Profile(properties: Map<ProfileKey, Serializable>?) :
    BaseModel<ProfileKey, Profile>(properties) {

    constructor() : this(null)

    fun setExternalId(identifier: String) = apply { this.externalId = identifier }
    var externalId: String?
        get() = (this[ProfileKey.EXTERNAL_ID]) as String?
        set(value) {
            this[ProfileKey.EXTERNAL_ID] = value
        }

    fun setEmail(email: String) = apply { this.email = email }
    var email: String?
        get() = (this[ProfileKey.EMAIL]) as String?
        set(value) {
            this[ProfileKey.EMAIL] = value
        }

    fun setPhoneNumber(phoneNumber: String) = apply { this.phoneNumber = phoneNumber }
    var phoneNumber: String?
        get() = (this[ProfileKey.PHONE_NUMBER]) as String?
        set(value) {
            this[ProfileKey.PHONE_NUMBER] = value
        }

    internal fun setAnonymousId(anonymousId: String) = apply { this.anonymousId = anonymousId }
    internal var anonymousId: String?
        get() = (this[ProfileKey.ANONYMOUS_ID]) as String
        set(value) {
            this[ProfileKey.ANONYMOUS_ID] = value
        }

    override fun setProperty(key: ProfileKey, value: Serializable) = apply {
        this[key] = value
    }

    override fun setProperty(key: String, value: Serializable) = apply {
        this[ProfileKey.CUSTOM(key)] = value
    }

    override fun merge(other: Profile) = apply { super.merge(other) }

    /**
     * Get a map of just the unique identifiers of this profile
     */
    internal fun getIdentifiers(): Map<ProfileKey, String> = mapOf(
        ProfileKey.EXTERNAL_ID to (this.externalId ?: ""),
        ProfileKey.EMAIL to (this.email ?: ""),
        ProfileKey.PHONE_NUMBER to (this.phoneNumber ?: ""),
        ProfileKey.ANONYMOUS_ID to (this.anonymousId ?: ""),
    ).filter { it.value.isNotEmpty() }
}

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
        EXTERNAL_ID, EMAIL, PHONE_NUMBER -> "$$name"
        else -> name
    }
}

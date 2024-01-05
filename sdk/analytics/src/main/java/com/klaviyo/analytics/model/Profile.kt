package com.klaviyo.analytics.model

import java.io.Serializable

/**
 * Controls the data that can be input into a map of profile attributes recognised by Klaviyo
 */
class Profile(properties: Map<ProfileKey, Serializable>?) :
    BaseModel<ProfileKey, Profile>(properties) {

    constructor() : this(null)

    fun setExternalId(identifier: String?) = apply { this.externalId = identifier }
    var externalId: String?
        get() = this[ProfileKey.EXTERNAL_ID]?.toString()
        set(value) {
            this[ProfileKey.EXTERNAL_ID] = value
        }

    fun setEmail(email: String?) = apply { this.email = email }
    var email: String?
        get() = this[ProfileKey.EMAIL]?.toString()
        set(value) {
            this[ProfileKey.EMAIL] = value
        }

    fun setPhoneNumber(phoneNumber: String?) = apply { this.phoneNumber = phoneNumber }
    var phoneNumber: String?
        get() = this[ProfileKey.PHONE_NUMBER]?.toString()
        set(value) {
            this[ProfileKey.PHONE_NUMBER] = value
        }

    internal fun setAnonymousId(anonymousId: String?) = apply { this.anonymousId = anonymousId }
    internal var anonymousId: String?
        get() = this[ProfileKey.ANONYMOUS_ID]?.toString()
        set(value) {
            this[ProfileKey.ANONYMOUS_ID] = value
        }

    override fun setProperty(key: ProfileKey, value: Serializable?) = apply {
        this[key] = value
    }

    override fun setProperty(key: String, value: Serializable?) =
        this.setProperty(ProfileKey.CUSTOM(key), value)

    override fun merge(other: Profile) = apply { super.merge(other) }
}

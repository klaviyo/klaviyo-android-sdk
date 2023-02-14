package com.klaviyo.analytics.model

import java.io.Serializable

/**
 * Controls the data that can be input into a map of profile attributes recognised by Klaviyo
 */
class Profile(properties: Map<ProfileKey, Serializable>?) :
    BaseModel<ProfileKey, Profile>(properties) {

    constructor() : this(null)

    private var appendMap: HashMap<String, Serializable> = HashMap()

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
        get() = (this[ProfileKey.ANONYMOUS]) as String
        set(value) {
            this[ProfileKey.ANONYMOUS] = value
        }

    // TODO internal?
    fun addAppendProperty(key: String, value: Serializable) = apply {
        appendMap[key] = value
        this[ProfileKey.APPEND] = appendMap
    }

    override fun setProperty(key: ProfileKey, value: Serializable) = apply {
        this[key] = value
    }

    override fun setProperty(key: String, value: Serializable) = apply {
        this[ProfileKey.CUSTOM(key)] = value
    }

    override fun merge(other: Profile) = apply {
        super.merge(other).also {
            other.appendMap.forEach { (k, v) -> addAppendProperty(k, v) }
        }
    }
}

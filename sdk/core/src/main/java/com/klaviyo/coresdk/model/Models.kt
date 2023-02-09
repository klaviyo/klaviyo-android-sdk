package com.klaviyo.coresdk.model

import java.io.Serializable

/**
 * Abstract class that wraps around a map to control access to its contents.
 * Provides helper functions to control the map's key type
 */
abstract class BaseAttributes<KeyType, Self>(properties: Map<KeyType, Serializable>?)
        where KeyType : KlaviyoKeyword, Self : BaseAttributes<KeyType, Self> {

    private val propertyMap: MutableMap<KeyType, Serializable> = mutableMapOf()

    init {
        properties?.forEach { (k, v) -> setProperty(k, v) }
    }

    operator fun get(key: KeyType): Serializable? = propertyMap[key]

    operator fun set(key: KeyType, value: Serializable?) {
        if (value == null) {
            propertyMap.remove(key)
        } else {
            propertyMap[key] = value
        }
    }

    operator fun set(key: String, value: Serializable) {
        this.setProperty(key, value)
    }

    /**
     * Convert this data model into a simple map
     */
    fun toMap(): Map<String, Serializable> = propertyMap.mapKeys { it.key.toString() }

    /**
     * Adds a custom property to the map.
     * Custom attributes can define any key name that isn't already reserved by Klaviyo
     */
    abstract fun setProperty(key: KeyType, value: Serializable): BaseAttributes<KeyType, Self>

    /**
     * Add a custom property to the map.
     * Custom attributes can define any key name that isn't already reserved by Klaviyo
     */
    abstract fun setProperty(key: String, value: Serializable): BaseAttributes<KeyType, Self>

    /**
     * Merges attributes from another object into this one
     *
     * @param other Second instance from which to merge properties
     */
    internal open fun merge(other: Self) = apply {
        other.propertyMap.forEach { (k, v) -> setProperty(k, v) }
    }
}

/**
 * Controls the data that can be input into a map of profile attributes recognised by Klaviyo
 */
class Profile(properties: Map<KlaviyoProfileAttributeKey, Serializable>?) :
    BaseAttributes<KlaviyoProfileAttributeKey, Profile>(properties) {

    constructor() : this(null)

    private var appendMap: HashMap<String, Serializable> = HashMap()

    fun setIdentifier(identifier: String) = apply { this.identifier = identifier }
    var identifier: String?
        get() = (this[KlaviyoProfileAttributeKey.EXTERNAL_ID]) as String?
        set(value) {
            this[KlaviyoProfileAttributeKey.EXTERNAL_ID] = value
        }

    fun setEmail(email: String) = apply { this.email = email }
    var email: String?
        get() = (this[KlaviyoProfileAttributeKey.EMAIL]) as String?
        set(value) {
            this[KlaviyoProfileAttributeKey.EMAIL] = value
        }

    fun setPhoneNumber(phoneNumber: String) = apply { this.phoneNumber = phoneNumber }
    var phoneNumber: String?
        get() = (this[KlaviyoProfileAttributeKey.PHONE_NUMBER]) as String?
        set(value) {
            this[KlaviyoProfileAttributeKey.PHONE_NUMBER] = value
        }

    internal fun setAnonymousId(anonymousId: String) = apply { this.anonymousId = anonymousId }
    internal var anonymousId: String?
        get() = (this[KlaviyoProfileAttributeKey.ANONYMOUS_ID]) as String
        set(value) {
            this[KlaviyoProfileAttributeKey.ANONYMOUS_ID] = value
        }

    fun addAppendProperty(key: String, value: Serializable) = apply {
        appendMap[key] = value
        this[KlaviyoProfileAttributeKey.APPEND] = appendMap
    }

    override fun setProperty(key: KlaviyoProfileAttributeKey, value: Serializable) = apply {
        this[key] = value
    }

    override fun setProperty(key: String, value: Serializable) = apply {
        this[KlaviyoProfileAttributeKey.CUSTOM(key)] = value
    }

    override fun merge(other: Profile) = apply {
        super.merge(other).also {
            other.appendMap.forEach { (k, v) -> addAppendProperty(k, v) }
        }
    }
}

/**
 * Controls the data that can be input into a map of event attributes recognised by Klaviyo
 */
class Event(val type: KlaviyoEventType, properties: Map<KlaviyoEventAttributeKey, Serializable>?) :
    BaseAttributes<KlaviyoEventAttributeKey, Event>(properties) {

    constructor(type: String, properties: Map<KlaviyoEventAttributeKey, Serializable>?) : this(
        KlaviyoEventType.CUSTOM(type), properties
    )

    constructor(type: KlaviyoEventType) : this(type, null)

    constructor(type: String) : this(type, null)

    fun setValue(value: String) = apply { this.value = value }
    var value: String
        get() = (this[KlaviyoEventAttributeKey.VALUE] ?: "") as String
        set(value) {
            this[KlaviyoEventAttributeKey.VALUE] = value
        }

    override fun setProperty(key: KlaviyoEventAttributeKey, value: Serializable) = apply {
        this[key] = value
    }

    override fun setProperty(key: String, value: Serializable) = apply {
        this[KlaviyoEventAttributeKey.CUSTOM(key)] = value
    }
}

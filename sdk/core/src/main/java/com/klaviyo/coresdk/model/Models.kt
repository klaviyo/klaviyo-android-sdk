package com.klaviyo.coresdk.model

import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils
import java.io.Serializable

/**
 * Abstract class that wraps around a map to control access to its contents.
 * Provides helper functions to control the map's key type
 */
abstract class BaseAttributes<KeyType> where KeyType : KlaviyoKeyword {

    private val propertyMap: MutableMap<KeyType, Serializable> = mutableMapOf()

    operator fun get(key: KeyType): Serializable? {
        return propertyMap[key]
    }

    operator fun set(key: KeyType, value: Serializable) {
        propertyMap[key] = value
    }

    /**
     * Convert this data model into a simple map
     */
    fun toMap(): Map<String, Serializable> {
        return propertyMap.mapKeys { it.key.toString() }
    }

    /**
     * Adds a custom property to the map.
     * Custom attributes can define any key name that isn't already reserved by Klaviyo
     */
    abstract fun setProperty(key: KeyType, value: Serializable): BaseAttributes<KeyType>

    /**
     * Add a custom property to the map.
     * Custom attributes can define any key name that isn't already reserved by Klaviyo
     */
    abstract fun setProperty(key: String, value: Serializable): BaseAttributes<KeyType>
}

/**
 * Controls the data that can be input into a map of customer attributes recognised by Klaviyo
 */
class Profile : BaseAttributes<KlaviyoProfileAttributeKey>() {

    private var appendMap: HashMap<String, Serializable> = HashMap()

    init {
        // TODO remove this and restructure how/when anonymous ID is set
        setAnonymousId(KlaviyoPreferenceUtils.readOrGenerateUUID())
    }

    fun setIdentifier(identifier: String) = apply { this.identifier = identifier }
    var identifier
        get() = (this[KlaviyoProfileAttributeKey.EXTERNAL_ID] ?: "") as String
        set(value) {
            this[KlaviyoProfileAttributeKey.EXTERNAL_ID] = value
        }

    fun setEmail(email: String) = apply { this.email = email }
    var email
        get() = (this[KlaviyoProfileAttributeKey.EMAIL] ?: "") as String
        set(value) {
            this[KlaviyoProfileAttributeKey.EMAIL] = value
        }

    fun setPhoneNumber(phoneNumber: String) = apply { this.phoneNumber = phoneNumber }
    var phoneNumber
        get() = (this[KlaviyoProfileAttributeKey.PHONE_NUMBER] ?: "") as String
        set(value) {
            this[KlaviyoProfileAttributeKey.PHONE_NUMBER] = value
        }

    internal fun setAnonymousId(anonymousId: String) = apply { this.anonymousId = anonymousId }
    internal var anonymousId
        get() = (this[KlaviyoProfileAttributeKey.ANONYMOUS_ID] ?: "") as String
        set(value) {
            this[KlaviyoProfileAttributeKey.ANONYMOUS_ID] = value
        }

    internal fun addAppendProperty(key: String, value: Serializable) = apply {
        appendMap[key] = value
        this[KlaviyoProfileAttributeKey.APPEND] = appendMap
    }

    override fun setProperty(key: KlaviyoProfileAttributeKey, value: Serializable) = apply {
        this[key] = value
    }

    override fun setProperty(key: String, value: Serializable) = apply {
        this[KlaviyoProfileAttributeKey.CUSTOM(key)] = value
    }
}

/**
 * Controls the data that can be input into a map of event attributes recognised by Klaviyo
 */
class Event : BaseAttributes<KlaviyoEventAttributeKey>() {

    // TODO add Type as a property so this encapsulates the whole of an event

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

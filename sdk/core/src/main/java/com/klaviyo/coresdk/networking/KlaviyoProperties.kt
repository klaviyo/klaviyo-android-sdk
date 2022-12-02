package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils
import java.io.Serializable

/**
 * Abstract class that wraps around a map to control access to its contents.
 * Provides helper functions to add properties to the map while controlling the keys available for entry
 */
abstract class KlaviyoProperties {
    internal val propertyMap: MutableMap<String, Serializable> = mutableMapOf()

    /**
     * Adds a new key/value pair to the map.
     * [KlaviyoPropertyKeys] adds some control to what keys our property maps recognise
     */
    open fun addProperty(propertyKey: KlaviyoPropertyKeys, value: String): KlaviyoProperties {
        propertyMap[propertyKey.name] = value
        return this
    }

    operator fun set(key: KlaviyoPropertyKeys, value: String) {
        addProperty(key, value)
    }

    /**
     * Adds a custom property to the map.
     * Custom properties can define any key name that isn't already reserved by Klaviyo
     */
    abstract fun addCustomProperty(key: String, value: Serializable): KlaviyoProperties

    /**
     * Fetches and returns our property map after converting it into a immutable map
     */
    internal fun toMap(): Map<Any, Any> {
        return propertyMap.toMap()
    }
}

/**
 * Controls the data that can be input into a map of customer properties recognised by Klaviyo
 */
class KlaviyoCustomerProperties : KlaviyoProperties() {

    init {
        setDefaultEmail()
    }

    /**
     * Adds an anonymous ID to the customer properties. This is fetched from existing configuration
     */
    internal fun setAnonymousId(): KlaviyoCustomerProperties {
        propertyMap[KlaviyoCustomerPropertyKeys.ANONYMOUS_ID.name] = KlaviyoPreferenceUtils.readOrGenerateUUID()
        return this
    }

    override fun addProperty(propertyKey: KlaviyoPropertyKeys, value: String): KlaviyoCustomerProperties {
        super.addProperty(propertyKey, value)
        return this
    }

    private fun setDefaultEmail() {
        val emailKey = KlaviyoCustomerPropertyKeys.EMAIL.name

        if (propertyMap[emailKey] == null && UserInfo.hasEmail()) {
            propertyMap[emailKey] = UserInfo.email
        } else if (propertyMap[emailKey] != null) {
            UserInfo.email = propertyMap[emailKey].toString()
        }
    }

    fun setIdentifier(value: String): KlaviyoCustomerProperties {
        propertyMap[KlaviyoCustomerPropertyKeys.ID.name] = value
        return this
    }

    fun setEmail(value: String): KlaviyoCustomerProperties {
        propertyMap[KlaviyoCustomerPropertyKeys.EMAIL.name] = value
        return this
    }

    fun setPhoneNumber(value: String): KlaviyoCustomerProperties {
        propertyMap[KlaviyoCustomerPropertyKeys.PHONE_NUMBER.name] = value
        return this
    }

    fun addAppendProperty(key: String, value: String): KlaviyoCustomerProperties {
        val appendKey = KlaviyoCustomerPropertyKeys.APPEND.name

        if (propertyMap.containsKey(appendKey)) {
            val appendMap = propertyMap[appendKey] as HashMap<String, Any>
            appendMap[key] = value
        } else {
            propertyMap[appendKey] = hashMapOf(key to value)
        }
        return this
    }

    fun addAppendProperty(key: String, value: HashMap<String, Any>): KlaviyoCustomerProperties {
        val appendKey = KlaviyoCustomerPropertyKeys.APPEND.name

        if (propertyMap.containsKey(appendKey)) {
            val appendMap = propertyMap[appendKey] as HashMap<String, Any>
            appendMap[key] = value
        } else {
            propertyMap[appendKey] = hashMapOf(key to value)
        }
        return this
    }

    override fun addCustomProperty(key: String, value: Serializable): KlaviyoCustomerProperties {
        propertyMap[KlaviyoCustomerPropertyKeys.CUSTOM(key).name] = value
        return this
    }
}

/**
 * Controls the data that can be input into a map of event properties recognised by Klaviyo
 */
class KlaviyoEventProperties : KlaviyoProperties() {

    override fun addProperty(propertyKey: KlaviyoPropertyKeys, value: String): KlaviyoEventProperties {
        super.addProperty(propertyKey, value)
        return this
    }

    fun addValue(value: String): KlaviyoEventProperties {
        propertyMap[KlaviyoEventPropertyKeys.VALUE.name] = value
        return this
    }

    override fun addCustomProperty(key: String, value: Serializable): KlaviyoEventProperties {
        propertyMap[KlaviyoEventPropertyKeys.CUSTOM(key).name] = value
        return this
    }
}

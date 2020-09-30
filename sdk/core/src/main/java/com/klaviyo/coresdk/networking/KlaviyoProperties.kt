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
    fun addProperty(propertyKey: KlaviyoPropertyKeys, value: String) {
        propertyMap[propertyKey.name] = value
    }

    /**
     * Adds a custom property to the map.
     * Custom properties can define any key name that isn't already reserved by Klaviyo
     */
    abstract fun addCustomProperty(key: String, value: Serializable)

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
class KlaviyoCustomerProperties: KlaviyoProperties() {

    init {
        setAnonymousId()
        setDefaultEmail()
    }

    /**
     * Adds an anonymous ID to the customer properties. This is fetched from existing configuration
     */
    internal fun setAnonymousId() {
        propertyMap[KlaviyoCustomerPropertyKeys.ANONYMOUS_ID.name] = "Android:${KlaviyoPreferenceUtils.readOrGenerateUUID()}"
    }

    internal fun setDefaultEmail() {
        val emailKey = KlaviyoCustomerPropertyKeys.EMAIL.name

        if (propertyMap[emailKey] == null && UserInfo.hasEmail()) {
            propertyMap[emailKey] = UserInfo.email
        } else if (propertyMap[emailKey] != null) {
            UserInfo.email = propertyMap[emailKey].toString()
        }
    }

    fun setIdentifier(value: String) {
        propertyMap[KlaviyoCustomerPropertyKeys.ID.name] = value
    }

    fun setEmail(value: String) {
        propertyMap[KlaviyoCustomerPropertyKeys.EMAIL.name] = value
    }

    fun setPhoneNumber(value: String) {
        propertyMap[KlaviyoCustomerPropertyKeys.PHONE_NUMBER.name] = value
    }

    fun addAppendedProperty(key: String, value: String) {
        val appendKey = KlaviyoCustomerPropertyKeys.APPEND.name

        if (propertyMap.containsKey(appendKey)) {
            val appendMap = propertyMap[appendKey] as HashMap<String, Any>
            appendMap[key] = value
        } else {
            propertyMap[appendKey] = hashMapOf(key to value)
        }
    }

    fun addAppendedProperty(key: String, value: HashMap<String, Any>) {
        val appendKey = KlaviyoCustomerPropertyKeys.APPEND.name

        if (propertyMap.containsKey(appendKey)) {
            val appendMap = propertyMap[appendKey] as HashMap<String, Any>
            appendMap[key] = value
        } else {
            propertyMap[appendKey] = hashMapOf(key to value)
        }
    }

    override fun addCustomProperty(key: String, value: Serializable) {
        propertyMap[KlaviyoCustomerPropertyKeys.CUSTOM(key).name] = value
    }
}

/**
 * Controls the data that can be input into a map of event properties recognised by Klaviyo
 */
class KlaviyoEventProperties: KlaviyoProperties() {
    fun addValue(value: String) {
        propertyMap[KlaviyoEventPropertyKeys.VALUE.name] = value
    }

    override fun addCustomProperty(key: String, value: Serializable) {
        propertyMap[KlaviyoEventPropertyKeys.CUSTOM(key).name] = value
    }
}
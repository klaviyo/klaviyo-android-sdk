package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.ConfigFileUtils

// Didn't inherit from a mutablemap class cause I don't want to expose the standard put options and force this API to be the only access the user has to the map
/**
 * Abstract class that wraps around a map to control access to its contents.
 * Provides helper functions to add properties to the map while controlling the keys available for entry
 */
abstract class KlaviyoProperties {
    internal val propertyMap: MutableMap<String, Any> = mutableMapOf()

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
    abstract fun addCustomProp(key: String, value: Any)

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
    /**
     * Adds an anonymous ID to the customer properties. This is fetched from existing configuration
     */
    internal fun addAnonymousId() {
        propertyMap[KlaviyoCustomerPropKeys.ANONYMOUS_ID.name] = "Android:${ConfigFileUtils.readOrCreateUUID()}"
    }

    fun addId(value: String) {
        propertyMap[KlaviyoCustomerPropKeys.ID.name] = value
    }

    fun addEmail(value: String) {
        propertyMap[KlaviyoCustomerPropKeys.EMAIL.name] = value
    }

    fun addPhoneNumber(value: String) {
        propertyMap[KlaviyoCustomerPropKeys.PHONE_NUMBER.name] = value
    }

    override fun addCustomProp(key: String, value: Any) {
        propertyMap[KlaviyoCustomerPropKeys.CUSTOM(key).name] = value
    }
}

/**
 * Controls the data that can be input into a map of event properties recognised by Klaviyo
 */
class KlaviyoEventProperties: KlaviyoProperties() {
    fun addValue(value: String) {
        propertyMap[KlaviyoEventPropKeys.VALUE.name] = value
    }

    override fun addCustomProp(key: String, value: Any) {
        propertyMap[KlaviyoEventPropKeys.CUSTOM(key).name] = value
    }
}
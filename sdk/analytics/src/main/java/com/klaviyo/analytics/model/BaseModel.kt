package com.klaviyo.analytics.model

import java.io.Serializable

/**
 * Abstract class that wraps around a map to control access to its contents.
 * Provides helper functions to control the map's key type
 */
abstract class BaseModel<Key, Self>(properties: Map<Key, Serializable>?)
        where Key : Keyword, Self : BaseModel<Key, Self> {

    private val propertyMap: MutableMap<Key, Serializable> = mutableMapOf()

    init {
        properties?.forEach { (k, v) -> setProperty(k, v) }
    }

    operator fun get(key: Key): Serializable? = propertyMap[key]

    operator fun set(key: Key, value: Serializable?) {
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
    abstract fun setProperty(key: Key, value: Serializable): BaseModel<Key, Self>

    /**
     * Add a custom property to the map.
     * Custom attributes can define any key name that isn't already reserved by Klaviyo
     */
    abstract fun setProperty(key: String, value: Serializable): BaseModel<Key, Self>

    /**
     * Merges attributes from another object into this one
     *
     * @param other Second instance from which to merge properties
     */
    open fun merge(other: Self) = apply {
        other.propertyMap.forEach { (k, v) -> setProperty(k, v) }
    }
}

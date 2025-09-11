package com.klaviyo.core.model

import com.klaviyo.core.Registry
import java.io.Serializable
import org.json.JSONArray
import org.json.JSONObject

/**
 * Base class for persistent observable properties that store serializable objects as JSON.
 * 
 * This class provides generic JSON deserialization logic that can be reused by subclasses
 * that need to persist complex objects. Subclasses implement the specific logic for
 * creating and populating their target type.
 *
 * @param T The type of object to persist (must be serializable)
 */
abstract class PersistentObservableSerializable<T>(
    key: Keyword,
    onChanged: PropertyObserver<T?> = { _, _ -> },
    fallback: () -> T? = { null }
) : PersistentObservableProperty<T?>(
    key = key,
    fallback = fallback,
    onChanged = onChanged
) {

    /**
     * Decode a JSON string to the target type T
     */
    override fun deserialize(storedValue: String?): T? = storedValue
        ?.takeIf { it.isNotEmpty() }
        ?.let {
            try {
                val json = JSONObject(storedValue)
                createInstance().also { instance ->
                    json.keys().forEach { key ->
                        populateInstance(instance, key, deserializeValue(json.get(key)))
                    }
                }
            } catch (e: Throwable) {
                Registry.log.warning("Invalid stored JSON for $key", e)
                null
            }
        }

    /**
     * Create a new instance of type T.
     * Subclasses must implement this to provide their specific type.
     */
    protected abstract fun createInstance(): T

    /**
     * Populate a field in the instance with a deserialized value.
     * Subclasses must implement this to handle their specific field assignment logic.
     *
     * @param instance The instance to populate
     * @param key The field key from JSON
     * @param value The deserialized value
     */
    protected abstract fun populateInstance(instance: T, key: String, value: Serializable)

    /**
     * Recursively decode JSON into [Serializable] for type-safety
     * when re-populating an object. This is generic and can be reused by any subclass.
     */
    protected fun deserializeValue(v: Any): Serializable = when (v) {
        is JSONArray -> Array(v.length()) { deserializeValue(v[it]) }
        is JSONObject -> HashMap<String, Serializable>(v.length()).also { map ->
            v.keys().forEach { key ->
                map[key] = deserializeValue(v.get(key))
            }
        }
        else -> v as Serializable
    }
}
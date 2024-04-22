package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Keyword
import com.klaviyo.core.Registry
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal abstract class PersistentObservableProperty<T>(
    val key: Keyword,
    private val default: T,
    private val fallback: () -> T = { default },
    private val onChanged: (property: PersistentObservableProperty<T>) -> Unit
) : ReadWriteProperty<Any?, T> {

    /**
     * Value of this property, backed by persistent store
     */
    private var value = default
        get() = field.takeIf { !isEmpty(it) } ?: fetch().also { field = it }
        set(newValue) {
            field = newValue
            persist(newValue)
        }

    /**
     * Public accessor to property's value
     */
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    /**
     * Set the value of this property if it passes validation rules from [validateChange]
     */
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        val oldValue = this.value

        if (validateChange(oldValue, value)) {
            this.value = value
            onChanged(this)
        }
    }

    /**
     * Reset the value to default in memory and on disk,
     * bypassing validation and callbacks
     */
    fun reset() { value = default }

    /**
     * Triggered by [setValue] to validate a change.
     * If this returns false, the property is not updated in memory or on disk.
     */
    protected open fun validateChange(oldValue: T, newValue: T): Boolean =
        if (oldValue == newValue) {
            Registry.log.info("Ignored update for $key, value is unchanged")
            false
        } else {
            true
        }

    /**
     * Overrideable method to determine if value is empty and [fetch] should be invoked
     *
     * Note: I had to use this instead of just a null check, because I don't want to assume
     * that all subclasses are using a nullable generic parameter
     */
    protected open fun isEmpty(value: T): Boolean = value != null

    /**
     * Serialize value for persistent store
     */
    protected open fun serialize(value: T): String = value.toString()

    /**
     * Deserialize from persistent store
     */
    abstract fun deserialize(storedValue: String): T

    /**
     * Save or clear property in the persistent store and return the persisted string value
     *
     * @param value
     * @return
     */
    private fun persist(value: T): String = serialize(value).also { serializedValue ->
        if (serializedValue.isEmpty()) {
            Registry.dataStore.clear(key.name)
        } else {
            Registry.dataStore.store(key.name, serializedValue)
        }
    }

    /**
     * Get value from persistent store or return a fallback if it isn't present
     *
     * @return
     */
    private fun fetch(): T = Registry.dataStore.fetch(key.name)
        .let { it.orEmpty() }
        .ifEmpty { persist(fallback()) }
        .let { deserialize(it) }
}

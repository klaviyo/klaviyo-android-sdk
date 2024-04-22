package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Keyword
import com.klaviyo.core.Registry
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal abstract class PersistentObservableProperty<T>(
    val key: Keyword,
    private val fallback: () -> T? = { null },
    private val onChanged: (property: PersistentObservableProperty<T>) -> Unit
) : ReadWriteProperty<Any?, T?> {

    /**
     * Value of this property, backed by persistent store
     */
    private var value: T? = null
        get() = field ?: fetch()?.also { field = it }
        set(newValue) {
            field = newValue
            persist(newValue)
        }

    /**
     * Public accessor to property's value
     */
    override fun getValue(thisRef: Any?, property: KProperty<*>): T? = value

    /**
     * Set the value of this property if it passes validation rules from [validateChange]
     */
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
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
    fun reset() { value = null }

    /**
     * Triggered by [setValue] to validate a change.
     * If this returns false, the property is not updated in memory or on disk.
     */
    protected open fun validateChange(oldValue: T?, newValue: T?): Boolean =
        if (oldValue == newValue) {
            Registry.log.info("Ignored update for $key, value is unchanged")
            false
        } else {
            true
        }

    /**
     * Deserialize from persistent store
     */
    abstract fun deserialize(storedValue: String?): T?

    /**
     * Save or clear property in the persistent store and return the persisted string value
     *
     * @param value
     * @return
     */
    private fun persist(value: T?): String = value?.toString()?.also { serializedValue ->
        Registry.dataStore.store(key.name, serializedValue)
    } ?: Registry.dataStore.clear(key.name).let { "" }

    /**
     * Get value from persistent store or return a fallback if it isn't present
     *
     * @return
     */
    private fun fetch(): T? = Registry.dataStore.fetch(key.name)?.let(::deserialize)
        ?: fallback()?.also(::persist)
}

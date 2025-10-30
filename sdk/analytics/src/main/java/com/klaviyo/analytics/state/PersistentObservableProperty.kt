package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Keyword
import com.klaviyo.core.Registry
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal typealias PropertyObserver<T> = (property: PersistentObservableProperty<T>, oldValue: T?) -> Unit

/**
 * Property delegate that is backed by the persistent store.
 *
 * When set, the value will be persisted to [key], and
 * on first get, [key] will be read from the store into memory.
 *
 * If no persistent value exists, the value provided by [fallback] will be
 * read into memory and saved to disk.
 *
 * [T] Should implement [toString] for serializing to persistent store,
 * and subclasses must implement [deserialize] for reading back.
 *
 * When the property's value changes, as detected by [validateChange],
 * then [onChanged] is triggered.
 *
 * @see [kotlin.properties.ObservableProperty] Inspiration for this class,
 * but it was simpler to re-implement, so we can access the private [value].
 */
internal abstract class PersistentObservableProperty<T>(
    val key: Keyword,
    private val fallback: () -> T? = { null },
    private val onChanged: PropertyObserver<T>
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
        if (validateChange(this.value, value)) {
            val oldValue = this.value
            this.value = value
            onChanged(this, oldValue)
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
            Registry.log.verbose("Ignored update for $key, value is unchanged")
            false
        } else {
            true
        }

    /**
     * Deserialize from persistent store
     */
    abstract fun deserialize(storedValue: String?): T?

    /**
     * Save or clear property in the persistent store
     */
    private fun persist(value: T?) = when (val serializedValue = value?.toString()) {
        null -> Registry.dataStore.clear(key.name)
        else -> Registry.dataStore.store(key.name, serializedValue)
    }

    /**
     * Get value from persistent store or return a fallback if it isn't present
     * If fallback is invoked, save its return value to persistent store
     *
     * @return
     */
    private fun fetch(): T? = Registry.dataStore.fetch(key.name)?.let(::deserialize)
        ?: fallback()?.also(::persist)
}

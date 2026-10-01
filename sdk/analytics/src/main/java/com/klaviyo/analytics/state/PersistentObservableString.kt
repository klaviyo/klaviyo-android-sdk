package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Keyword
import com.klaviyo.core.Registry
import kotlin.reflect.KProperty

internal class PersistentObservableString(
    key: Keyword,
    onChanged: PropertyObserver<String?> = { _, _ -> },
    fallback: () -> String? = { null }
) : PersistentObservableProperty<String?>(
    key = key,
    fallback = fallback,
    onChanged = onChanged
) {
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
        val trimmedValue = value?.trim()

        if (trimmedValue != value) {
            Registry.log.verbose("Trimmed whitespace from ${property.name}.")
        }

        super.setValue(thisRef, property, trimmedValue)
    }

    override fun validateChange(oldValue: String?, newValue: String?): Boolean {
        if (newValue.isNullOrEmpty()) {
            warnEmptyValue()
            return false
        }

        return super.validateChange(oldValue, newValue)
    }

    /**
     * Set the trimmed value in memory and on disk, bypassing validation and callbacks.
     * A blank value clears the property, logging [warnEmptyValueCleared].
     */
    override fun replace(newValue: String?) {
        val trimmedValue = newValue?.trim()

        if (trimmedValue?.isEmpty() == true) {
            warnEmptyValueCleared()
        }

        super.replace(trimmedValue?.ifEmpty { null })
    }

    /**
     * Log a warning that an empty value was given for this property and the property was cleared
     */
    fun warnEmptyValueCleared() = Registry.log.warning(
        "Empty string value for $key, value cleared."
    )

    private fun warnEmptyValue() = Registry.log.warning(
        "Empty string value for $key will be ignored."
    )

    override fun deserialize(storedValue: String?): String? = storedValue
}

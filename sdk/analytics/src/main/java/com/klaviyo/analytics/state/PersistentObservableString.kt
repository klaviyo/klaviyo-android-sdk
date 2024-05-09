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
            Registry.log.warning("Empty string value for $key will be ignored.")
            return false
        }

        return super.validateChange(oldValue, newValue)
    }

    override fun deserialize(storedValue: String?): String? = storedValue
}

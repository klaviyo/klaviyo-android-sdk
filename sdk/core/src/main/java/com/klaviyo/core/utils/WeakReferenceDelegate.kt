package com.klaviyo.core.utils

import java.lang.ref.WeakReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class WeakReferenceDelegate<T>(initialValue: T? = null) : ReadWriteProperty<Any?, T?> {
    private var weakRefValue = WeakReference<T?>(initialValue)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T? = weakRefValue.get()

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) = if (value == null) {
        weakRefValue.clear()
    } else {
        weakRefValue = WeakReference(value)
    }
}

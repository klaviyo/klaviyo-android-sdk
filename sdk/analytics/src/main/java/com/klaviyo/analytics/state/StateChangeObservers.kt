package com.klaviyo.analytics.state

import androidx.annotation.RestrictTo
import com.klaviyo.core.Registry
import java.util.concurrent.CopyOnWriteArrayList

/**
 * SDK module observers. Registration is available before analytics initialization. Delivery is
 * synchronous in registration order, before observers registered on [State].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class StateChangeObservers {
    private val observers = CopyOnWriteArrayList<StateChangeObserver>()

    fun add(observer: StateChangeObserver) {
        observers.addIfAbsent(observer)
    }

    fun remove(observer: StateChangeObserver) {
        observers.remove(observer)
    }

    internal fun dispatch(change: StateChange) {
        observers.forEach { observer ->
            try {
                observer(change)
            } catch (e: Exception) {
                Registry.log.error("State change observer failed", e)
            }
        }
    }
}

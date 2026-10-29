package com.klaviyo.forms.bridge

import kotlin.time.Duration

/**
 * Interface for managing a list of [JsBridgeObserver] abstractly
 */
internal interface JsBridgeObserverCollection {
    /**
     * List of observers managed by this instance
     */
    val observers: List<JsBridgeObserver>

    /**
     * Start all observers in the collection
     */
    fun startObservers(forEvent: NativeBridgeMessage) = apply {
        observers
            .filter { it.startOn == forEvent }
            .forEach { it.startObserver() }
    }

    fun startObservers(forEvent: NativeBridgeMessage, eventReplayWindow: Duration) = apply {
        observers
            .filter { it.startOn == forEvent }
            .forEach {
                if (it is ProfileEventObserver) it.startObserver(eventReplayWindow) else it.startObserver()
            }
    }

    /**
     * Stop all observers in the collection
     */
    fun stopObservers() = apply { observers.forEach { it.stopObserver() } }
}

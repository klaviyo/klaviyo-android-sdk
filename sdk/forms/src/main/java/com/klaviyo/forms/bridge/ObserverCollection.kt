package com.klaviyo.forms.bridge

/**
 * Interface for managing a list of [Observer] abstractly
 */
internal interface ObserverCollection {
    /**
     * List of observers managed by this instance
     */
    val observers: List<Observer>

    /**
     * Compiles the handshake data from all observers into a single list
     */
    val handshake: List<HandshakeSpec> get() = observers.map { it.handshake }

    /**
     * Start all observers in the collection
     */
    fun startObservers() = observers.forEach { it.startObserver() }

    /**
     * Stop all observers in the collection
     */
    fun stopObservers() = observers.forEach { it.stopObserver() }
}

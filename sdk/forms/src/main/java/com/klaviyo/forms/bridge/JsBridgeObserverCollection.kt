package com.klaviyo.forms.bridge

/**
 * Interface for managing a list of [JsBridgeObserver] abstractly
 */
internal interface JsBridgeObserverCollection {
    /**
     * List of observers managed by this instance
     */
    val observers: List<JsBridgeObserver>

    /**
     * Compiles the handshake data from all observers into a single list
     */
    val handshake: List<HandshakeSpec> get() = observers.mapNotNull { it.handshake }

    /**
     * Start all observers in the collection
     */
    fun startObservers() = apply { observers.forEach { it.startObserver() } }

    /**
     * Stop all observers in the collection
     */
    fun stopObservers() = apply { observers.forEach { it.stopObserver() } }
}

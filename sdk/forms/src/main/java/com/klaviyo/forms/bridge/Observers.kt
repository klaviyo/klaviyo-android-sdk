package com.klaviyo.forms.bridge

/**
 * Interface for managing a list of observers
 */
interface Observers {
    val observers: List<Observer>
    fun startObservers() = observers.forEach { it.startObserver() }
    fun stopObservers() = observers.forEach { it.stopObserver() }
}

package com.klaviyo.core.model

typealias StoreObserver = (key: String, value: String?) -> Unit

/**
 * Simple interface for a data persistence "engine" that can read and write to disk
 *
 * To keep things very simple, this interface only expects a key/value storage with strings.
 * JSON-encoding is the simplest way to leverage this store with non-string data,
 * which of course means accessors must implement type safety checks as necessary.
 */
interface DataStore {

    /**
     * Retrieve the value for the given key from the persistent store
     *
     * @param key
     * @return The stored value, or null if the key is not set
     */
    fun fetch(key: String): String?

    /**
     * Save a key/value pair to the persistent store
     *
     * @param key
     * @param value
     */
    fun store(key: String, value: String)

    /**
     * Remove a key from the persistent store
     *
     * @param key
     */
    fun clear(key: String)

    /**
     * Register an observer to be notified when any changes are made to persistent store
     *
     * @param observer
     */
    fun onStoreChange(observer: StoreObserver)

    /**
     * De-register an observer previously added with [onStoreChange]
     *
     * @param observer
     */
    fun offStoreChange(observer: StoreObserver)
}

/**
 * Fetch a key from store, or generate and store a new value if not found
 *
 * @param key
 * @param fallback
 */
fun DataStore.fetchOrCreate(key: String, fallback: () -> String): String =
    fetch(key) ?: fallback().also { store(key, it) }

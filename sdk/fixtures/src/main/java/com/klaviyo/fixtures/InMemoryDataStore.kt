package com.klaviyo.fixtures

import com.klaviyo.core.model.DataStore
import com.klaviyo.core.model.StoreObserver

/**
 * Implementation of DataStore that just uses an in-memory map
 * for mocking/unit tests
 */
class InMemoryDataStore : DataStore {
    private val store: MutableMap<String, String> = mutableMapOf()

    override fun fetch(key: String): String? {
        return store[key]
    }

    override fun store(key: String, value: String) {
        store[key] = value
    }

    override fun clear(key: String) {
        store.remove(key)
    }

    // Test fixture doesn't need an observer implementation
    override fun onStoreChange(observer: StoreObserver) {}
    override fun offStoreChange(observer: StoreObserver) {}
}

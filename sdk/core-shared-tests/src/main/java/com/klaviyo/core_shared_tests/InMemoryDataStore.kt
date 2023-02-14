package com.klaviyo.core_shared_tests

import com.klaviyo.coresdk.model.DataStore

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
}

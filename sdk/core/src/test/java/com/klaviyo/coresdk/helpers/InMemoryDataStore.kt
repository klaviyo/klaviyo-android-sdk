package com.klaviyo.coresdk.helpers

import com.klaviyo.coresdk.model.DataStore

/**
 * Implementation of DataStore that just uses an in-memory map
 * for mocking/unit tests
 */
internal object InMemoryDataStore : DataStore {
    private val store: MutableMap<String, String> = mutableMapOf()

    override fun fetch(key: String): String? {
        return store[key]
    }

    override fun store(key: String, value: String) {
        store[key] = value
    }
}

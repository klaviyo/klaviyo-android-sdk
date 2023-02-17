package com.klaviyo.core.model

/**
 * Simple interface for a data persistence "engine" that can read and write to disk
 *
 * To keep things very simple, this interface only expects a key/value storage with strings.
 * JSON-encoding is the simplest way to leverage this store with non-string data,
 * which of course means accessors must implement type safety checks as necessary.
 */
interface DataStore {
    fun fetch(key: String): String?

    fun store(key: String, value: String)

    fun clear(key: String)
}

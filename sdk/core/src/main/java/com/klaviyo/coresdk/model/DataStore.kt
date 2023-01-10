package com.klaviyo.coresdk.model

// TODO we should really support async operations for this
/**
 * Simple interface for a data persistence "engine" that can read and write to disk
 */
internal interface DataStore {
    fun fetch(key: String): String?

    fun store(key: String, value: String)
}

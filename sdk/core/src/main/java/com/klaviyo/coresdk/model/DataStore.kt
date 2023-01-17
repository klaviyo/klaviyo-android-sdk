package com.klaviyo.coresdk.model

/**
 * Simple interface for a data persistence "engine" that can read and write to disk
 */
interface DataStore {
    fun fetch(key: String): String?

    fun store(key: String, value: String)

    // TODO should we support async+callback methods?
    //  SharedPreferences doesn't support it so I didn't put it in this interface yet
}

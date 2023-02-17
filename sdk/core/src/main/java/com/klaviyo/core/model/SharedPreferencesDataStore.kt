package com.klaviyo.core.model

import android.content.Context
import android.content.SharedPreferences
import com.klaviyo.core.Registry

/**
 * Simple DataStore implementation using SharedPreferences for persistence
 *
 * To keep things very simple, this interface only expects a key/value storage with strings.
 * JSON-encoding is the simplest way to leverage this store with non-string data,
 * which of course means accessors must implement type safety checks as necessary.
 */
internal object SharedPreferencesDataStore : DataStore {
    internal const val KLAVIYO_PREFS_NAME = "KlaviyoSDKPreferences"

    /**
     * Opens the Klaviyo SDK's shared preferences file
     *
     * @return The Klaviyo SDK's shared preferences opened in private mode
     */
    private fun openSharedPreferences(): SharedPreferences {
        return Registry.config.applicationContext.getSharedPreferences(
            KLAVIYO_PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    /**
     * Opens the shared preferences and writes a given key/value pair
     *
     * The write operation is performed async, but has no callback
     *
     * @param key The identifying key that the value being written will go by
     * @param value The value that we are writing to the shared preferences
     */
    override fun store(key: String, value: String) {
        openSharedPreferences()
            .edit()
            .putString(key, value)
            .apply()
    }

    /**
     * Opens the shared preferences and reads the value of a given key
     *
     * @param key The identifying key of the value we want to read
     *
     * @return The value read from the shared preferences for the given key
     */
    override fun fetch(key: String): String? {
        return openSharedPreferences().getString(key, "")
    }

    /**
     * Remove a value from shared preferences if set
     *
     * @param key The identifying key to remove from persistent store
     */
    override fun clear(key: String) {
        openSharedPreferences()
            .edit()
            .remove(key)
            .apply()
    }
}

package com.klaviyo.coresdk.utils

import com.klaviyo.coresdk.model.DataStore
import com.klaviyo.coresdk.model.SharedPreferencesDataStore
import java.util.UUID

/**
 * Used to interface with the shared preferences of the Klaviyo SDK
 */
object KlaviyoPreferenceUtils : DataStore {
    internal const val KLAVIYO_UUID_KEY = "UUID"

    /**
     * Attempts to read a UUID from the shared preferences.
     * If no UUID was found, it will generate a fresh one and save that to the shared preferences
     *
     * @return The UUID that was read or generated
     */
    internal fun readOrGenerateUUID(): String {
        var uuid = fetch(KLAVIYO_UUID_KEY)
        if (uuid.isNullOrEmpty()) {
            uuid = UUID.randomUUID().toString()
            store(KLAVIYO_UUID_KEY, uuid)
        }
        return uuid
    }

    /**
     * Opens the shared preferences and writes a given key/value pair
     *
     * @param key The identifying key that the value being written will go by
     * @param value The value that we are writing to the shared preferences
     */
    override fun store(key: String, value: String) = SharedPreferencesDataStore.store(key, value)

    /**
     * Opens the shared preferences and reads the value of a given key
     *
     * @param key The identifying key of the value we want to read
     *
     * @return The value read from the shared preferences for the given key
     */
    override fun fetch(key: String): String? = SharedPreferencesDataStore.fetch(key)
}

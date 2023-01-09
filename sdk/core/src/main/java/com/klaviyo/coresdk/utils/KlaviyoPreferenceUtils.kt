package com.klaviyo.coresdk.utils

import android.content.Context
import android.content.SharedPreferences
import com.klaviyo.coresdk.KlaviyoConfig
import java.util.UUID

/**
 * Used to interface with the shared preferences of the Klaviyo SDK
 */
object KlaviyoPreferenceUtils : DataStoreInterface {
    private const val KLAVIYO_PREFS_NAME = "KlaviyoSDKPreferences"

    internal const val KLAVIYO_UUID_KEY = "UUID"

    /**
     * Opens the Klaviyo SDK's shared preferences file
     *
     * @return The Klaviyo SDK's shared preferences opened in private mode
     */
    private fun openSharedPreferences(): SharedPreferences {
        return KlaviyoConfig.applicationContext.getSharedPreferences(
            KLAVIYO_PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

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
    override fun store(key: String, value: String) {
        val editor = openSharedPreferences().edit()
        editor.putString(key, value)
        editor.apply()
    }

    /**
     * Opens the shared preferences and reads the value of a given key
     *
     * @param key The identifying key of the value we want to read
     *
     * @return The value read from the shared preferences for the given key
     */
    override fun fetch(key: String): String? {
        return openSharedPreferences().getString(key, null)
    }
}

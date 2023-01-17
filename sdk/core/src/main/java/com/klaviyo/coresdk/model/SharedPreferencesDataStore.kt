package com.klaviyo.coresdk.model

import android.content.Context
import android.content.SharedPreferences
import com.klaviyo.coresdk.KlaviyoConfig

object SharedPreferencesDataStore : DataStore {
    internal const val KLAVIYO_PREFS_NAME = "KlaviyoSDKPreferences"

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
}
